package com.personal.medisynth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResearchRequestTest extends AbstractIntegrationTest {

    private static final String BODY = """
            {"datasetTitle":"Synthetic cardiac cohort","purpose":"Model validation study","dataScope":"Age, diagnosis"}""";

    private Actor admin, researcher1, researcher2, patient, doctor;

    @BeforeEach
    void people() throws Exception {
        admin = createAdmin();
        researcher1 = createResearcher(admin, "r1@medisynth.test");
        researcher2 = createResearcher(admin, "r2@medisynth.test");
        patient = registerPatient("p@medisynth.test");
        doctor = createVerifiedDoctor(admin, "doc@medisynth.test", "MCI-1");
    }

    private long submit(Actor researcher) throws Exception {
        MvcResult r = mvc.perform(authPost("/api/research/requests", researcher.token(), BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL")).andReturn();
        return ((Number) read(r, "$.id")).longValue();
    }

    @Test
    void researcherSubmitsAndSeesOnlyTheirOwnRequests() throws Exception {
        submit(researcher1);
        mvc.perform(authGet("/api/research/requests/my", researcher1.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].researcherInstitution").value("Northbridge Health Institute"));
        mvc.perform(authGet("/api/research/requests/my", researcher2.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void onlyResearchersCanSubmit() throws Exception {
        for (Actor a : new Actor[] { patient, doctor, admin }) {
            mvc.perform(authPost("/api/research/requests", a.token(), BODY)).andExpect(status().isForbidden());
        }
        assertThat(count("SELECT count(*) FROM research_access_requests")).isZero();
    }

    @Test
    void adminApprovesAndTheDecisionIsRecordedAndAudited() throws Exception {
        long id = submit(researcher1);
        mvc.perform(authGet("/api/admin/research-requests?status=PENDING_APPROVAL", admin.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(authPatch("/api/admin/research-requests/" + id + "/approve", admin.token(), "{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedByName").value("Root Admin"));
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'RESEARCH_REQUEST_APPROVED'")).isEqualTo(1);
        mvc.perform(authGet("/api/research/requests/my", researcher1.token()))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"));
    }

    @Test
    void adminRejectsWithAReasonAndCannotDecideTwice() throws Exception {
        long id = submit(researcher1);
        mvc.perform(authPatch("/api/admin/research-requests/" + id + "/reject", admin.token(), "{\"reason\":\"Scope too broad\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Scope too broad"));
        mvc.perform(authPatch("/api/admin/research-requests/" + id + "/approve", admin.token(), "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_ALREADY_DECIDED"));
    }

    @Test
    void nonAdminsCannotDecideOrReadTheAdminList() throws Exception {
        long id = submit(researcher1);
        for (Actor a : new Actor[] { researcher1, patient, doctor }) {
            mvc.perform(authPatch("/api/admin/research-requests/" + id + "/approve", a.token(), "{}")).andExpect(status().isForbidden());
            mvc.perform(authGet("/api/admin/research-requests", a.token())).andExpect(status().isForbidden());
            mvc.perform(authGet("/api/admin/audit-logs", a.token())).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM research_access_requests WHERE id = ?", String.class, id))
                .isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void invalidRequestsAndPendingLimitAreEnforced() throws Exception {
        mvc.perform(authPost("/api/research/requests", researcher1.token(), "{\"datasetTitle\":\"\",\"purpose\":\"\"}"))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 10; i++) {
            submit(researcher1);
        }
        mvc.perform(authPost("/api/research/requests", researcher1.token(), BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOO_MANY_PENDING_REQUESTS"));
    }

    @Test
    void adminCanReadTheAuditTrail() throws Exception {
        submit(researcher1);
        mvc.perform(authGet("/api/admin/audit-logs?size=50", admin.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").exists());
    }
}
