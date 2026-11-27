package com.personal.medisynth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffRegistrationTest extends AbstractIntegrationTest {

    @Test
    void labTechnicianRegistrationIsPendingWithNoTokenAndCannotLogIn() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", labTechJson("lab@medisynth.test", "EMP-1")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT requested_role FROM registration_requests", String.class))
                .isEqualTo("LAB_TECHNICIAN");
        // the role is NOT granted yet
        assertThat(jdbc.queryForObject("SELECT granted_role FROM users", String.class)).isEqualTo("PATIENT");
        mvc.perform(jsonPost("/api/auth/login", """
                {"email":"lab@medisynth.test","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_PENDING_APPROVAL"));
    }

    @Test
    void researcherRegistrationIsPendingWithNoToken() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", researcherJson("res@medisynth.test")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accessToken").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT requested_role FROM registration_requests", String.class))
                .isEqualTo("RESEARCHER");
    }

    @Test
    void approvalGrantsExactlyTheRequestedRole() throws Exception {
        Actor admin = createAdmin();
        createLabTech(admin, "lab@medisynth.test", "EMP-1");
        createResearcher(admin, "res@medisynth.test");
        assertThat(jdbc.queryForObject("SELECT granted_role FROM users WHERE email = 'lab@medisynth.test'", String.class))
                .isEqualTo("LAB_TECHNICIAN");
        assertThat(jdbc.queryForObject("SELECT granted_role FROM users WHERE email = 'res@medisynth.test'", String.class))
                .isEqualTo("RESEARCHER");
    }

    @Test
    void rejectedApplicantsCannotLogIn() throws Exception {
        Actor admin = createAdmin();
        mvc.perform(jsonPost("/api/auth/register", researcherJson("res@medisynth.test"))).andExpect(status().isAccepted());
        mvc.perform(authPatch("/api/admin/registration-requests/" + requestIdFor("res@medisynth.test") + "/reject",
                admin.token(), "{\"reason\":\"Unknown institution\"}")).andExpect(status().isOk());
        mvc.perform(jsonPost("/api/auth/login", """
                {"email":"res@medisynth.test","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_REJECTED"));
    }

    @Test
    void roleSpecificDetailsAreRequired() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", """
                {"name":"L","email":"l@medisynth.test","password":"%s","phone":"+919811111111","role":"LAB_TECHNICIAN"}"""
                .formatted(PASSWORD))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.department").exists())
                .andExpect(jsonPath("$.fieldErrors.employeeId").exists());
        mvc.perform(jsonPost("/api/auth/register", """
                {"name":"R","email":"r@medisynth.test","password":"%s","phone":"+919811111111","role":"RESEARCHER"}"""
                .formatted(PASSWORD))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.institution").exists());
        assertThat(count("SELECT count(*) FROM users")).isZero();
    }

    @Test
    void employeeIdAndEmailMustBeUnique() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", labTechJson("a@medisynth.test", "EMP-9"))).andExpect(status().isAccepted());
        mvc.perform(jsonPost("/api/auth/register", labTechJson("b@medisynth.test", "emp-9")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPLOYEE_ID_ALREADY_REGISTERED"));
        mvc.perform(jsonPost("/api/auth/register", labTechJson("a@medisynth.test", "EMP-10")))
                .andExpect(status().isConflict());
    }

    @Test
    void adminSeesTheApplicantDetails() throws Exception {
        Actor admin = createAdmin();
        mvc.perform(jsonPost("/api/auth/register", labTechJson("lab@medisynth.test", "EMP-1"))).andExpect(status().isAccepted());
        mvc.perform(authGet("/api/admin/registration-requests?status=PENDING_APPROVAL&requestedRole=LAB_TECHNICIAN", admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicantDetails.department").value("Pathology"))
                .andExpect(jsonPath("$.content[0].applicantDetails.employeeId").value("EMP-1"));
    }

    @Test
    void adminRoleCanStillNeverBeRequested() throws Exception {
        mvc.perform(jsonPost("/api/auth/register", """
                {"name":"E","email":"e@medisynth.test","password":"%s","phone":"+919811111111","role":"ADMIN"}"""
                .formatted(PASSWORD))).andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM users")).isZero();
    }
}
