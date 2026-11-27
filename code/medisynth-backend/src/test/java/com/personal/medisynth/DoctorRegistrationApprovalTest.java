package com.personal.medisynth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DoctorRegistrationApprovalTest extends AbstractIntegrationTest {

    private static final String DOC = "doc@medisynth.test";

    // ------------------------------------------------------------------ submission

    @Test
    void doctorRegistrationIsPersistedAsPendingApprovalWithoutAnyToken() throws Exception {
        MvcResult result = mvc.perform(jsonPost("/api/auth/register", doctorJson(DOC, "MCI-1001")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.registrationReference").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist())
                .andReturn();
        String reference = read(result, "$.registrationReference");

        assertThat(jdbc.queryForObject("SELECT status FROM registration_requests", String.class)).isEqualTo("PENDING_APPROVAL");
        assertThat(jdbc.queryForObject("SELECT requested_role FROM registration_requests", String.class)).isEqualTo("DOCTOR");
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE email = ?", String.class, DOC))
                .isEqualTo("PENDING_APPROVAL");
        assertThat(jdbc.queryForObject("SELECT verification_status FROM doctor_profiles", String.class)).isEqualTo("PENDING");
        // the raw reference is never stored, only its hash
        assertThat(count("SELECT count(*) FROM registration_requests WHERE status_token_hash = ?", reference)).isZero();
    }

    @Test
    void pendingRequestIsStillThereAfterReloadingFromTheDatabase() throws Exception {
        registerDoctor(DOC, "MCI-1001");
        // fresh queries on a fresh connection state: nothing is cached in memory
        assertThat(count("SELECT count(*) FROM registration_requests WHERE status = 'PENDING_APPROVAL'")).isEqualTo(1);
        Actor admin = createAdmin();
        mvc.perform(authGet("/api/admin/registration-requests?status=PENDING_APPROVAL", admin.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pendingDoctorCannotLogInAndHasNoDoctorPrivileges() throws Exception {
        registerDoctor(DOC, "MCI-1001");

        MvcResult login = mvc.perform(jsonPost("/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(DOC, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_APPROVAL")).andReturn();
        assertThat(bodyOf(login)).doesNotContain("accessToken");

        // Even a token signed with the REAL key is useless: status is re-read from the database.
        String forged = forgeToken(userId(DOC), Instant.now().plus(1, ChronoUnit.HOURS), "medisynth", "DOCTOR");
        mvc.perform(authGet("/api/doctors/me", forged)).andExpect(status().isUnauthorized());
        mvc.perform(authGet("/api/auth/me", forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicatePendingRequestAndDuplicateLicenseAreRejected() throws Exception {
        registerDoctor(DOC, "MCI-1001");

        mvc.perform(jsonPost("/api/auth/register", doctorJson("DOC@medisynth.test", "MCI-9999")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_ALREADY_PENDING"));
        mvc.perform(jsonPost("/api/auth/register", doctorJson("other@medisynth.test", "mci-1001")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LICENSE_ALREADY_REGISTERED"));

        assertThat(count("SELECT count(*) FROM registration_requests")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM users")).isEqualTo(1);
    }

    @Test
    void existingPatientEmailCannotBeReusedForADoctorRequest() throws Exception {
        registerPatient(DOC);
        mvc.perform(jsonPost("/api/auth/register", doctorJson(DOC, "MCI-1001")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        assertThat(count("SELECT count(*) FROM registration_requests")).isZero();
    }

    @Test
    void doctorApplicationRequiresProfessionalDetails() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", """
                {"name":"Dr X","email":"x@medisynth.test","password":"%s","phone":"+919876543210","role":"DOCTOR"}"""
                .formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.licenseNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.specialization").exists())
                .andExpect(jsonPath("$.fieldErrors.qualifications").exists())
                .andExpect(jsonPath("$.fieldErrors.experienceYears").exists());
        assertThat(count("SELECT count(*) FROM users")).isZero();
    }

    // ------------------------------------------------------------------ public status check

    @Test
    void statusCheckIsScopedToOneRequestAndLeaksNothingElse() throws Exception {
        String reference = registerDoctor(DOC, "MCI-1001");
        registerDoctor("other@medisynth.test", "MCI-2002");

        MvcResult r = mvc.perform(get("/api/auth/registration-status").param("reference", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.requestedRole").value("DOCTOR")).andReturn();
        String body = bodyOf(r);
        assertThat(body).doesNotContain(DOC).doesNotContain("other@medisynth.test").doesNotContain("+9198")
                .doesNotContain("MCI-").doesNotContain("applicant");

        mvc.perform(get("/api/auth/registration-status").param("reference", "not-a-real-reference"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/registration-status")).andExpect(status().isBadRequest());
        // a numeric id is NOT a reference: requests cannot be enumerated
        mvc.perform(get("/api/auth/registration-status").param("reference", String.valueOf(requestIdFor(DOC))))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ admin review

    @Test
    void adminCanListPendingRequestsWithFiltersAndPaging() throws Exception {
        registerDoctor(DOC, "MCI-1001");
        registerDoctor("doc2@medisynth.test", "MCI-2002");
        Actor admin = createAdmin();

        MvcResult r = mvc.perform(authGet("/api/admin/registration-requests?status=PENDING_APPROVAL&requestedRole=DOCTOR&size=1&page=0",
                admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.content[0].doctorProfile.verificationStatus").value("PENDING"))
                .andReturn();
        assertThat(bodyOf(r)).doesNotContain("passwordHash").doesNotContain("$2");

        mvc.perform(authGet("/api/admin/registration-requests?status=APPROVED", admin.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(authGet("/api/admin/registration-requests/" + requestIdFor(DOC), admin.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applicantEmail").value(DOC));
        mvc.perform(authGet("/api/admin/registration-requests/999999", admin.token())).andExpect(status().isNotFound());
        mvc.perform(authGet("/api/admin/registration-requests?status=BOGUS", admin.token())).andExpect(status().isBadRequest());
    }

    @Test
    void approvalUpdatesTheRequestAndActivatesTheAccountTogether() throws Exception {
        registerDoctor(DOC, "MCI-1001");
        Actor admin = createAdmin();
        long requestId = requestIdFor(DOC);

        mvc.perform(authPatch("/api/admin/registration-requests/" + requestId + "/approve", admin.token(), "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedById").value(admin.userId()))
                .andExpect(jsonPath("$.doctorProfile.verificationStatus").value("VERIFIED"));

        assertThat(jdbc.queryForObject("SELECT status FROM registration_requests WHERE id = ?", String.class, requestId))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT reviewed_by FROM registration_requests WHERE id = ?", Long.class, requestId))
                .isEqualTo(admin.userId());
        assertThat(jdbc.queryForObject("SELECT reviewed_at FROM registration_requests WHERE id = ?", java.sql.Timestamp.class, requestId))
                .isNotNull();
        assertThat(jdbc.queryForObject("SELECT granted_role FROM users WHERE email = ?", String.class, DOC)).isEqualTo("DOCTOR");
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE email = ?", String.class, DOC)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT verification_status FROM doctor_profiles", String.class)).isEqualTo("VERIFIED");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'REGISTRATION_APPROVED' AND actor_id = ?", admin.userId()))
                .isEqualTo(1);

        // now the doctor can log in as a DOCTOR and use doctor features
        mvc.perform(jsonPost("/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(DOC, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("DOCTOR"));
        mvc.perform(authGet("/api/doctors/me", login(DOC))).andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
    }

    @Test
    void approvalIsAllOrNothing() throws Exception {
        registerDoctor(DOC, "MCI-1001");
        Actor admin = createAdmin();
        long requestId = requestIdFor(DOC);
        // Sabotage the last step of the approval (the doctor profile lookup happens AFTER the request and user were modified)
        jdbc.update("DELETE FROM doctor_profiles");

        mvc.perform(authPatch("/api/admin/registration-requests/" + requestId + "/approve", admin.token(), "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCTOR_PROFILE_MISSING"));

        // Nothing was half-applied
        assertThat(jdbc.queryForObject("SELECT status FROM registration_requests WHERE id = ?", String.class, requestId))
                .isEqualTo("PENDING_APPROVAL");
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE email = ?", String.class, DOC))
                .isEqualTo("PENDING_APPROVAL");
        assertThat(jdbc.queryForObject("SELECT granted_role FROM users WHERE email = ?", String.class, DOC)).isEqualTo("PATIENT");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'REGISTRATION_APPROVED'")).isZero();
    }

    @Test
    void aRequestCanOnlyBeDecidedOnce() throws Exception {
        registerDoctor(DOC, "MCI-1001");
        registerDoctor("doc2@medisynth.test", "MCI-2002");
        Actor admin = createAdmin();
        long first = requestIdFor(DOC);
        long second = requestIdFor("doc2@medisynth.test");

        mvc.perform(authPatch("/api/admin/registration-requests/" + first + "/approve", admin.token(), "{}")).andExpect(status().isOk());
        mvc.perform(authPatch("/api/admin/registration-requests/" + first + "/approve", admin.token(), "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_ALREADY_DECIDED"));
        mvc.perform(authPatch("/api/admin/registration-requests/" + first + "/reject", admin.token(), "{\"reason\":\"changed my mind\"}"))
                .andExpect(status().isConflict());

        mvc.perform(authPatch("/api/admin/registration-requests/" + second + "/reject", admin.token(), "{}")).andExpect(status().isOk());
        mvc.perform(authPatch("/api/admin/registration-requests/" + second + "/approve", admin.token(), "{}"))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT granted_role FROM users WHERE email = 'doc2@medisynth.test'", String.class))
                .isEqualTo("PATIENT");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'REGISTRATION_APPROVED'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'REGISTRATION_REJECTED'")).isEqualTo(1);
    }

    @Test
    void concurrentApprovalsOnlyApplyOnce() throws Exception {
        registerDoctor(DOC, "MCI-1001");
        Actor admin = createAdmin();
        mvc.perform(authPost("/api/admin/users/admins", admin.token(), adminJson("admin2@medisynth.test")))
                .andExpect(status().isCreated());
        Actor admin2 = new Actor(login("admin2@medisynth.test"), userId("admin2@medisynth.test"), 0);
        long requestId = requestIdFor(DOC);
        String t1 = admin.token();
        String t2 = admin2.token();

        var results = runConcurrently(java.util.List.of(
                () -> mvc.perform(authPatch("/api/admin/registration-requests/" + requestId + "/approve", t1, "{}"))
                        .andReturn().getResponse().getStatus(),
                () -> mvc.perform(authPatch("/api/admin/registration-requests/" + requestId + "/reject", t2, "{}"))
                        .andReturn().getResponse().getStatus()));

        assertThat(results).containsExactlyInAnyOrder(200, 409);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action IN ('REGISTRATION_APPROVED','REGISTRATION_REJECTED')"))
                .isEqualTo(1);
    }

    @Test
    void rejectionIsPersistedAndBlocksLogin() throws Exception {
        String reference = registerDoctor(DOC, "MCI-1001");
        Actor admin = createAdmin();
        long requestId = requestIdFor(DOC);

        mvc.perform(authPatch("/api/admin/registration-requests/" + requestId + "/reject", admin.token(),
                "{\"reason\":\"License could not be verified\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(jdbc.queryForObject("SELECT status FROM registration_requests WHERE id = ?", String.class, requestId)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT rejection_reason FROM registration_requests WHERE id = ?", String.class, requestId))
                .isEqualTo("License could not be verified");
        assertThat(jdbc.queryForObject("SELECT reviewed_by FROM registration_requests WHERE id = ?", Long.class, requestId))
                .isEqualTo(admin.userId());
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE email = ?", String.class, DOC)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT granted_role FROM users WHERE email = ?", String.class, DOC)).isNotEqualTo("DOCTOR");
        assertThat(jdbc.queryForObject("SELECT verification_status FROM doctor_profiles", String.class)).isEqualTo("REJECTED");

        // rejected user cannot log in at all, so no doctor (or any) privileges
        MvcResult login = mvc.perform(jsonPost("/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(DOC, PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_REJECTED")).andReturn();
        assertThat(bodyOf(login)).doesNotContain("accessToken");
        String forged = forgeToken(userId(DOC), Instant.now().plus(1, ChronoUnit.HOURS), "medisynth", "DOCTOR");
        mvc.perform(authGet("/api/doctors/me", forged)).andExpect(status().isUnauthorized());

        // the applicant learns the outcome through the safe status check; the decision stays in admin history
        mvc.perform(get("/api/auth/registration-status").param("reference", reference))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("License could not be verified"));
        mvc.perform(authGet("/api/admin/registration-requests?status=REJECTED", admin.token()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void approvedApplicantSeesApprovedStatusAndAppearsInTheDoctorDirectory() throws Exception {
        String reference = registerDoctor(DOC, "MCI-1001");
        Actor admin = createAdmin();
        Actor patient = registerPatient("pat@medisynth.test");

        // pending doctors are invisible in the directory
        mvc.perform(authGet("/api/doctors", patient.token())).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(authPatch("/api/admin/registration-requests/" + requestIdFor(DOC) + "/approve", admin.token(), "{}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/registration-status").param("reference", reference))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        MvcResult list = mvc.perform(authGet("/api/doctors", patient.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andReturn();
        // directory shows no private details
        assertThat(bodyOf(list)).doesNotContain(DOC).doesNotContain("MCI-1001").doesNotContain("+9198");
    }

    @Test
    void anAdminCannotApproveTheirOwnRequest() throws Exception {
        Actor admin = createAdmin();
        jdbc.update("INSERT INTO registration_requests (user_id, requested_role, status, status_token_hash, submitted_at) "
                + "VALUES (?, 'DOCTOR', 'PENDING_APPROVAL', ?, now())", admin.userId(), "a".repeat(64));
        long requestId = jdbc.queryForObject("SELECT id FROM registration_requests", Long.class);

        mvc.perform(authPatch("/api/admin/registration-requests/" + requestId + "/approve", admin.token(), "{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"));
        assertThat(jdbc.queryForObject("SELECT status FROM registration_requests WHERE id = ?", String.class, requestId))
                .isEqualTo("PENDING_APPROVAL");
    }
}
