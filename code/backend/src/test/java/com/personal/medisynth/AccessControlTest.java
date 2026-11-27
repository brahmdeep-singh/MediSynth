package com.personal.medisynth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessControlTest extends AbstractIntegrationTest {

    private static final String[] ADMIN_GETS = {
            "/api/admin/users", "/api/admin/patients", "/api/admin/doctors", "/api/admin/appointments",
            "/api/admin/registration-requests", "/api/admin/registration-requests/999999999"};

    @Test
    void patientsAndDoctorsCannotReachAdminEndpoints() throws Exception {
        Actor admin = createAdmin();
        Actor doctor = createVerifiedDoctor(admin, "doc@medisynth.test", "MCI-1001");
        Actor patient = registerPatient("pat@medisynth.test");

        for (String url : ADMIN_GETS) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(authGet(url, patient.token())).andExpect(status().isForbidden());
            mvc.perform(authGet(url, doctor.token())).andExpect(status().isForbidden());
            mvc.perform(authGet(url, admin.token())).andExpect(status().is(url.endsWith("/999999999") ? 404 : 200));
        }
        long requestId = requestIdFor("doc@medisynth.test");
        for (Actor a : new Actor[] {patient, doctor}) {
            mvc.perform(authPatch("/api/admin/registration-requests/" + requestId + "/approve", a.token(), "{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(authPatch("/api/admin/registration-requests/" + requestId + "/reject", a.token(), "{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(authPatch("/api/admin/users/" + admin.userId() + "/status", a.token(), "{\"status\":\"SUSPENDED\"}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, admin.userId())).isEqualTo("ACTIVE");
    }

    @Test
    void patientsCannotUseDoctorEndpointsAndViceVersa() throws Exception {
        Actor admin = createAdmin();
        Actor doctor = createVerifiedDoctor(admin, "doc@medisynth.test", "MCI-1001");
        Actor patient = registerPatient("pat@medisynth.test");

        mvc.perform(authGet("/api/doctors/me", patient.token())).andExpect(status().isForbidden());
        mvc.perform(authGet("/api/doctors/me/appointments", patient.token())).andExpect(status().isForbidden());
        mvc.perform(authGet("/api/patients/me", doctor.token())).andExpect(status().isForbidden());
        mvc.perform(authGet("/api/patients/me/appointments", doctor.token())).andExpect(status().isForbidden());
        mvc.perform(authGet("/api/patients/me", admin.token())).andExpect(status().isForbidden());
    }

    @Test
    void invalidTokensAreRejectedWith401() throws Exception {
        Actor patient = registerPatient("pat@medisynth.test");
        mvc.perform(authGet("/api/auth/me", patient.token())).andExpect(status().isOk());

        // garbage
        mvc.perform(authGet("/api/auth/me", "not.a.jwt")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        // tampered signature
        String t = patient.token();
        String tampered = t.substring(0, t.length() - 2) + (t.endsWith("AA") ? "BB" : "AA");
        mvc.perform(authGet("/api/auth/me", tampered)).andExpect(status().isUnauthorized());
        // tampered payload (different user id, original signature)
        String[] parts = t.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String swapped = payload.replaceFirst("\"sub\":\"\\d+\"", "\"sub\":\"1\"");
        String forgedPayload = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(swapped.getBytes()) + "." + parts[2];
        mvc.perform(authGet("/api/auth/me", forgedPayload)).andExpect(status().isUnauthorized());
        // unsigned ("alg":"none") token
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + patient.userId() + "\",\"iss\":\"medisynth\",\"exp\":" + (Instant.now().getEpochSecond() + 3600) + "}").getBytes());
        mvc.perform(authGet("/api/auth/me", header + "." + body + ".")).andExpect(status().isUnauthorized());
        // wrong scheme
        mvc.perform(get("/api/auth/me").header("Authorization", "Basic abc")).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokensAreRejectedButTheSameForgeryWithAFutureExpiryWorks() throws Exception {
        Actor patient = registerPatient("pat@medisynth.test");

        // control: proves the forging helper uses the right key, so the failure below is really about expiry
        String valid = forgeToken(patient.userId(), Instant.now().plus(1, ChronoUnit.HOURS), "medisynth", "PATIENT");
        mvc.perform(authGet("/api/auth/me", valid)).andExpect(status().isOk());

        String expired = forgeToken(patient.userId(), Instant.now().minus(5, ChronoUnit.MINUTES), "medisynth", "PATIENT");
        mvc.perform(authGet("/api/auth/me", expired)).andExpect(status().isUnauthorized());

        String wrongIssuer = forgeToken(patient.userId(), Instant.now().plus(1, ChronoUnit.HOURS), "someone-else", "PATIENT");
        mvc.perform(authGet("/api/auth/me", wrongIssuer)).andExpect(status().isUnauthorized());
    }

    @Test
    void theRoleClaimInATokenIsNeverTrusted() throws Exception {
        Actor patient = registerPatient("pat@medisynth.test");
        String claimsAdmin = forgeToken(patient.userId(), Instant.now().plus(1, ChronoUnit.HOURS), "medisynth", "ADMIN");

        mvc.perform(authGet("/api/auth/me", claimsAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PATIENT"));
        mvc.perform(authGet("/api/admin/users", claimsAdmin)).andExpect(status().isForbidden());
    }

    @Test
    void suspendingAUserRevokesTheirExistingTokenImmediately() throws Exception {
        Actor admin = createAdmin();
        Actor patient = registerPatient("pat@medisynth.test");
        mvc.perform(authGet("/api/auth/me", patient.token())).andExpect(status().isOk());

        mvc.perform(authPatch("/api/admin/users/" + patient.userId() + "/status", admin.token(), "{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));

        mvc.perform(authGet("/api/auth/me", patient.token())).andExpect(status().isUnauthorized());
        mvc.perform(jsonPost("/api/auth/login", """
                {"email":"pat@medisynth.test","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'USER_STATUS_CHANGED'")).isEqualTo(1);

        mvc.perform(authPatch("/api/admin/users/" + patient.userId() + "/status", admin.token(), "{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(authGet("/api/auth/me", patient.token())).andExpect(status().isOk());
    }

    @Test
    void statusManagementRulesAreEnforced() throws Exception {
        Actor admin = createAdmin();
        registerDoctor("doc@medisynth.test", "MCI-1001");
        long pendingUser = userId("doc@medisynth.test");

        mvc.perform(authPatch("/api/admin/users/" + admin.userId() + "/status", admin.token(), "{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SELF_STATUS_CHANGE_FORBIDDEN"));
        mvc.perform(authPatch("/api/admin/users/" + pendingUser + "/status", admin.token(), "{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USE_REGISTRATION_WORKFLOW"));
        mvc.perform(authPatch("/api/admin/users/" + pendingUser + "/status", admin.token(), "{\"status\":\"PENDING_APPROVAL\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(authPatch("/api/admin/users/999999/status", admin.token(), "{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(authPatch("/api/admin/users/" + pendingUser + "/status", admin.token(), "{}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE id = ?", String.class, pendingUser))
                .isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void adminListsSupportFiltersAndPaging() throws Exception {
        Actor admin = createAdmin();
        registerPatient("p1@medisynth.test");
        registerPatient("p2@medisynth.test");
        registerDoctor("doc@medisynth.test", "MCI-1001");

        mvc.perform(authGet("/api/admin/users?role=PATIENT&size=1", admin.token()))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(authGet("/api/admin/users?status=PENDING_APPROVAL", admin.token()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(authGet("/api/admin/patients", admin.token())).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(authGet("/api/admin/doctors?verificationStatus=PENDING", admin.token()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(authGet("/api/admin/users?size=100000", admin.token())).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void corsAllowsOnlyTheConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/auth/login").header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void errorsNeverLeakInternals() throws Exception {
        Actor patient = registerPatient("pat@medisynth.test");
        String body = bodyOf(mvc.perform(authGet("/api/appointments/not-a-number", patient.token()))
                .andExpect(status().isBadRequest()).andReturn());
        assertThat(body).doesNotContain("NumberFormatException").doesNotContain("at com.").doesNotContain("org.springframework");
        mvc.perform(authGet("/api/does-not-exist", patient.token())).andExpect(status().isNotFound());
    }
}
