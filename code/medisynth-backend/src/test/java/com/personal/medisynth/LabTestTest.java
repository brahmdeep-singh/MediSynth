package com.personal.medisynth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LabTestTest extends AbstractIntegrationTest {

    private Actor admin, doctorA, doctorB, patient1, patient2, tech1, tech2, researcher;
    private long apptA1; // patient1 with doctorA

    @BeforeEach
    void people() throws Exception {
        admin = createAdmin();
        doctorA = createVerifiedDoctor(admin, "docA@medisynth.test", "MCI-A");
        doctorB = createVerifiedDoctor(admin, "docB@medisynth.test", "MCI-B");
        patient1 = registerPatient("p1@medisynth.test");
        patient2 = registerPatient("p2@medisynth.test");
        tech1 = createLabTech(admin, "t1@medisynth.test", "EMP-1");
        tech2 = createLabTech(admin, "t2@medisynth.test", "EMP-2");
        researcher = createResearcher(admin, "res@medisynth.test");
        MvcResult r = mvc.perform(authPost("/api/appointments", patient1.token(),
                bookingJson(doctorA.profileId(), slot(3, 10, 0), "Chest pain"))).andExpect(status().isCreated()).andReturn();
        apptA1 = ((Number) read(r, "$.id")).longValue();
    }

    private long order(Actor doctor, long appointmentId) throws Exception {
        MvcResult r = mvc.perform(authPost("/api/lab-tests", doctor.token(),
                """
                {"appointmentId":%d,"testType":"Complete blood count","notes":"Fasting"}""".formatted(appointmentId)))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) read(r, "$.id")).longValue();
    }

    @Test
    void fullWorkflowFromOrderToResult() throws Exception {
        long id = order(doctorA, apptA1);
        assertThat(jdbc.queryForObject("SELECT patient_id FROM lab_test_requests WHERE id = ?", Long.class, id))
                .isEqualTo(patient1.userId());

        mvc.perform(authGet("/api/lab/tests?status=REQUESTED", tech1.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].patientName").value("Test Patient"));
        mvc.perform(authPatch("/api/lab/tests/" + id + "/start", tech1.token(), "{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(authPatch("/api/lab/tests/" + id + "/complete", tech1.token(), "{\"resultSummary\":\"Hb 13.2 g/dL, normal\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        // the patient and the ordering doctor both see the result
        mvc.perform(authGet("/api/lab-tests/my", patient1.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].resultSummary").value("Hb 13.2 g/dL, normal"));
        mvc.perform(authGet("/api/lab-tests/my", doctorA.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action IN ('LAB_TEST_ORDERED','LAB_TEST_STARTED','LAB_TEST_COMPLETED')"))
                .isEqualTo(3);
    }

    @Test
    void aDoctorCannotOrderForSomeoneElsesAppointment() throws Exception {
        mvc.perform(authPost("/api/lab-tests", doctorB.token(),
                "{\"appointmentId\":" + apptA1 + ",\"testType\":\"X-ray\"}")).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM lab_test_requests")).isZero();
    }

    @Test
    void onlyDoctorsOrderAndOnlyTechniciansWorkTheQueue() throws Exception {
        String body = "{\"appointmentId\":" + apptA1 + ",\"testType\":\"CBC\"}";
        for (Actor a : new Actor[] { patient1, tech1, researcher, admin }) {
            mvc.perform(authPost("/api/lab-tests", a.token(), body)).andExpect(status().isForbidden());
        }
        long id = order(doctorA, apptA1);
        for (Actor a : new Actor[] { patient1, doctorA, researcher, admin }) {
            mvc.perform(authGet("/api/lab/tests", a.token())).andExpect(status().isForbidden());
            mvc.perform(authPatch("/api/lab/tests/" + id + "/start", a.token(), "{}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/lab/tests")).andExpect(status().isUnauthorized());
    }

    @Test
    void patientsAndDoctorsOnlySeeTheirOwnTests() throws Exception {
        order(doctorA, apptA1);
        mvc.perform(authGet("/api/lab-tests/my", patient2.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(authGet("/api/lab-tests/my", doctorB.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(authGet("/api/lab-tests/my", tech1.token())).andExpect(status().isForbidden());
    }

    @Test
    void stateRulesAreEnforced() throws Exception {
        long id = order(doctorA, apptA1);
        // cannot complete before starting
        mvc.perform(authPatch("/api/lab/tests/" + id + "/complete", tech1.token(), "{\"resultSummary\":\"x\"}"))
                .andExpect(status().isConflict());
        mvc.perform(authPatch("/api/lab/tests/" + id + "/start", tech1.token(), "{}")).andExpect(status().isOk());
        // cannot start twice, and another technician cannot finish it
        mvc.perform(authPatch("/api/lab/tests/" + id + "/start", tech2.token(), "{}")).andExpect(status().isConflict());
        mvc.perform(authPatch("/api/lab/tests/" + id + "/complete", tech2.token(), "{\"resultSummary\":\"x\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_YOUR_TEST"));
        // a result is required
        mvc.perform(authPatch("/api/lab/tests/" + id + "/complete", tech1.token(), "{\"resultSummary\":\"  \"}"))
                .andExpect(status().isBadRequest());
        // a started test can no longer be cancelled
        mvc.perform(authPost("/api/lab-tests/" + id + "/cancel", doctorA.token(), "{}")).andExpect(status().isConflict());
    }

    @Test
    void theOrderingDoctorCanCancelAnUnstartedTest() throws Exception {
        long id = order(doctorA, apptA1);
        mvc.perform(authPost("/api/lab-tests/" + id + "/cancel", doctorB.token(), "{}")).andExpect(status().isNotFound());
        mvc.perform(authPost("/api/lab-tests/" + id + "/cancel", doctorA.token(), "{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(authPatch("/api/lab/tests/" + id + "/start", tech1.token(), "{}")).andExpect(status().isConflict());
    }

    @Test
    void cannotOrderForACancelledAppointmentOrWithoutATestType() throws Exception {
        mvc.perform(authPost("/api/appointments/" + apptA1 + "/cancel", patient1.token(), "{}")).andExpect(status().isOk());
        mvc.perform(authPost("/api/lab-tests", doctorA.token(), "{\"appointmentId\":" + apptA1 + ",\"testType\":\"CBC\"}"))
                .andExpect(status().isConflict());
        mvc.perform(authPost("/api/lab-tests", doctorA.token(), "{\"appointmentId\":" + apptA1 + ",\"testType\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSuspendedTechnicianLosesAccessImmediately() throws Exception {
        mvc.perform(authPatch("/api/admin/users/" + tech1.userId() + "/status", admin.token(), "{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        mvc.perform(authGet("/api/lab/tests", tech1.token())).andExpect(status().isUnauthorized());
    }
}
