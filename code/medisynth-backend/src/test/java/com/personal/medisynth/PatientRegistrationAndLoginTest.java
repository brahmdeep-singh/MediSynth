package com.personal.medisynth;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PatientRegistrationAndLoginTest extends AbstractIntegrationTest {

    @Test
    void patientRegistrationIsSavedAndActivatedImmediately() throws Exception {
        MvcResult result = mvc.perform(jsonPost("/api/auth/register", patientJson("pat@medisynth.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("ACTIVE"))
                .andExpect(jsonPath("$.user.role").value("PATIENT"))
                .andExpect(jsonPath("$.user.email").value("pat@medisynth.test"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        // Persisted in PostgreSQL with server-decided role/status and a BCrypt hash
        assertThat(jdbc.queryForObject("SELECT granted_role FROM users WHERE email = 'pat@medisynth.test'", String.class))
                .isEqualTo("PATIENT");
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE email = 'pat@medisynth.test'", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'pat@medisynth.test'", String.class))
                .startsWith("$2").doesNotContain(PASSWORD);
        assertThat(count("SELECT count(*) FROM registration_requests")).isZero();

        // The response never contains a password or hash
        assertThat(bodyOf(result)).doesNotContain("password").doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void patientCanLogInWithoutAdminApproval() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", patientJson("pat@medisynth.test"))).andExpect(status().isCreated());

        mvc.perform(jsonPost("/api/auth/login", """
                {"email":"PAT@medisynth.test","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("PATIENT"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // and the token reaches patient-only features
        String token = login("pat@medisynth.test");
        mvc.perform(authGet("/api/patients/me", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pat@medisynth.test"));
    }

    @Test
    void clientSuppliedRoleStatusAndIdAreIgnored() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", """
                {"name":"Sneaky","email":"sneaky@medisynth.test","password":"%s","phone":"+919876543210",
                 "role":"PATIENT","grantedRole":"ADMIN","accountStatus":"PENDING_APPROVAL","id":9999,"isAdmin":true}"""
                .formatted(PASSWORD))).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT granted_role FROM users WHERE email='sneaky@medisynth.test'", String.class))
                .isEqualTo("PATIENT");
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE email='sneaky@medisynth.test'", String.class))
                .isEqualTo("ACTIVE");
        assertThat(userId("sneaky@medisynth.test")).isNotEqualTo(9999L);
    }

    @Test
    void publicAdminRegistrationIsRejectedAndCreatesNothing() throws Exception {
        for (String role : new String[] {"ADMIN", "admin", " Admin "}) {
            mvc.perform(jsonPost("/api/auth/register", """
                    {"name":"Evil","email":"evil@medisynth.test","password":"%s","phone":"+919876543210","role":"%s"}"""
                    .formatted(PASSWORD, role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_REGISTRATION_FORBIDDEN"));
        }
        assertThat(count("SELECT count(*) FROM users")).isZero();
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", """
                {"name":"X","email":"x@medisynth.test","password":"%s","phone":"+919876543210","role":"SUPERUSER"}"""
                .formatted(PASSWORD))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
        assertThat(count("SELECT count(*) FROM users")).isZero();
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", patientJson("dup@medisynth.test"))).andExpect(status().isCreated());
        mvc.perform(jsonPost("/api/auth/register", patientJson("DUP@Medisynth.test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        assertThat(count("SELECT count(*) FROM users")).isEqualTo(1);
    }

    @Test
    void invalidInputGetsFieldErrors() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", """
                {"name":"","email":"not-an-email","password":"short","phone":"abc","role":"PATIENT"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
        assertThat(count("SELECT count(*) FROM users")).isZero();
    }

    @Test
    void malformedJsonGetsSafeError() throws Exception {
        MvcResult r = mvc.perform(jsonPost("/api/auth/register", "{not json"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(bodyOf(r)).doesNotContain("Exception").doesNotContain("at com.");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", patientJson("pat@medisynth.test"))).andExpect(status().isCreated());

        mvc.perform(jsonPost("/api/auth/login", """
                {"email":"pat@medisynth.test","password":"Wrong-password-1"}"""))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(jsonPost("/api/auth/login", """
                {"email":"nobody@medisynth.test","password":"Wrong-password-1"}"""))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(authGet("/api/auth/me", "garbage")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        Actor patient = registerPatient("pat@medisynth.test");
        mvc.perform(authGet("/api/auth/me", patient.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.userId()))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
