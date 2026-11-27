package com.personal.medisynth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyntheticDatasetTest extends AbstractIntegrationTest {

    private Actor admin, researcher1, researcher2, patient;

    @BeforeEach
    void people() throws Exception {
        admin = createAdmin();
        researcher1 = createResearcher(admin, "r1@medisynth.test");
        researcher2 = createResearcher(admin, "r2@medisynth.test");
        patient = registerPatient("p@medisynth.test");
    }

    private static String createBody(int records) {
        return "{\"name\":\"Outpatient batch\",\"description\":\"Test batch\",\"recordCount\":" + records + ",\"seed\":42}";
    }

    private long create(int records) throws Exception {
        MvcResult r = mvc.perform(authPost("/api/admin/datasets", admin.token(), createBody(records)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EVALUATING"))
                .andExpect(jsonPath("$.report").doesNotExist()).andReturn();
        return ((Number) read(r, "$.id")).longValue();
    }

    private long readyDataset() throws Exception {
        long id = create(5000);
        mvc.perform(authPost("/api/admin/datasets/" + id + "/evaluate", admin.token(), "{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        return id;
    }

    private long requestAccess(Actor researcher, long datasetId) throws Exception {
        MvcResult r = mvc.perform(authPost("/api/research/requests", researcher.token(),
                "{\"datasetId\":" + datasetId + ",\"purpose\":\"Model validation\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) read(r, "$.id")).longValue();
    }

    private void approve(long requestId) throws Exception {
        mvc.perform(authPatch("/api/admin/research-requests/" + requestId + "/approve", admin.token(), "{}"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminsRunThePipeline() throws Exception {
        for (Actor a : new Actor[] { patient, researcher1 }) {
            mvc.perform(authPost("/api/admin/datasets", a.token(), createBody(5000))).andExpect(status().isForbidden());
        }
        assertThat(count("SELECT count(*) FROM synthetic_datasets")).isZero();
    }

    @Test
    void generationStoresTheRowsAndValidatesSize() throws Exception {
        long id = create(5000);
        assertThat(count("SELECT count(*) FROM synthetic_patient_records WHERE dataset_id = ?", id)).isEqualTo(5000);
        mvc.perform(authPost("/api/admin/datasets", admin.token(), createBody(100))).andExpect(status().isBadRequest());
        mvc.perform(authPost("/api/admin/datasets", admin.token(), createBody(60000))).andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'DATASET_GENERATED'")).isEqualTo(1);
    }

    @Test
    void aLargeDatasetPassesEvaluationAndCannotBeEvaluatedTwice() throws Exception {
        long id = create(5000);
        mvc.perform(authPost("/api/admin/datasets/" + id + "/evaluate", admin.token(), "{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.report.passed").value(true))
                .andExpect(jsonPath("$.report.checks.length()").value(8));
        mvc.perform(authPost("/api/admin/datasets/" + id + "/evaluate", admin.token(), "{}")).andExpect(status().isConflict());
    }

    @Test
    void aTooSmallDatasetFailsThenPassesAfterRegeneratingBigger() throws Exception {
        long id = create(500);
        mvc.perform(authPost("/api/admin/datasets/" + id + "/evaluate", admin.token(), "{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEEDS_REGENERATION"))
                .andExpect(jsonPath("$.report.failureReasons[0]").exists());
        mvc.perform(authPost("/api/admin/datasets/" + id + "/regenerate", admin.token(), "{\"recordCount\":10000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EVALUATING"))
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.recordCount").value(10000))
                .andExpect(jsonPath("$.report").doesNotExist());
        assertThat(count("SELECT count(*) FROM synthetic_patient_records WHERE dataset_id = ?", id)).isEqualTo(10000);
        mvc.perform(authPost("/api/admin/datasets/" + id + "/evaluate", admin.token(), "{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        mvc.perform(authPost("/api/admin/datasets/" + id + "/regenerate", admin.token(), "{}")).andExpect(status().isConflict());
    }

    @Test
    void theCatalogueShowsOnlyReadyDatasets() throws Exception {
        create(5000); // stays EVALUATING
        long ready = readyDataset();
        mvc.perform(authGet("/api/research/datasets", researcher1.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(ready))
                .andExpect(jsonPath("$.content[0].accessStatus").doesNotExist());
        mvc.perform(authGet("/api/research/datasets", patient.token())).andExpect(status().isForbidden());
    }

    @Test
    void accessFlowRequestApproveThenPreviewAndDownload() throws Exception {
        long ds = readyDataset();
        long req = requestAccess(researcher1, ds);

        // pending: no rows yet
        mvc.perform(authGet("/api/research/datasets/" + ds + "/preview", researcher1.token())).andExpect(status().isForbidden());
        mvc.perform(authGet("/api/research/datasets/" + ds + "/download", researcher1.token())).andExpect(status().isForbidden());
        // a second request for the same dataset is a duplicate
        mvc.perform(authPost("/api/research/requests", researcher1.token(),
                "{\"datasetId\":" + ds + ",\"purpose\":\"again\"}")).andExpect(status().isConflict());

        approve(req);
        mvc.perform(authGet("/api/research/datasets/" + ds + "/preview?size=10", researcher1.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].syntheticId").exists());
        MvcResult csv = mvc.perform(authGet("/api/research/datasets/" + ds + "/download", researcher1.token()))
                .andExpect(status().isOk()).andReturn();
        String[] lines = bodyOf(csv).split("\n");
        assertThat(lines).hasSize(5001);
        assertThat(lines[0]).startsWith("syntheticId,ageYears,gender");
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'DATASET_DOWNLOADED'")).isEqualTo(1);

        // the other researcher has no grant
        mvc.perform(authGet("/api/research/datasets/" + ds + "/download", researcher2.token())).andExpect(status().isForbidden());
        // the catalogue now shows the approved status
        mvc.perform(authGet("/api/research/datasets", researcher1.token()))
                .andExpect(jsonPath("$.content[0].accessStatus").value("APPROVED"));
    }

    @Test
    void aRejectedRequestGivesNoAccessButCanBeRepeated() throws Exception {
        long ds = readyDataset();
        long req = requestAccess(researcher1, ds);
        mvc.perform(authPatch("/api/admin/research-requests/" + req + "/reject", admin.token(), "{}")).andExpect(status().isOk());
        mvc.perform(authGet("/api/research/datasets/" + ds + "/download", researcher1.token())).andExpect(status().isForbidden());
        requestAccess(researcher1, ds); // allowed again after a rejection
    }

    @Test
    void retiringADatasetEndsAccessAtOnce() throws Exception {
        long ds = readyDataset();
        approve(requestAccess(researcher1, ds));
        mvc.perform(authGet("/api/research/datasets/" + ds + "/download", researcher1.token())).andExpect(status().isOk());
        mvc.perform(authPost("/api/admin/datasets/" + ds + "/retire", admin.token(), "{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETIRED"));
        mvc.perform(authGet("/api/research/datasets/" + ds + "/download", researcher1.token())).andExpect(status().isNotFound());
        mvc.perform(authGet("/api/research/datasets", researcher1.token())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(authPost("/api/admin/datasets/" + ds + "/retire", admin.token(), "{}")).andExpect(status().isConflict());
    }

    @Test
    void requestsCanOnlyNameReadyDatasets() throws Exception {
        long notReady = create(5000);
        mvc.perform(authPost("/api/research/requests", researcher1.token(),
                "{\"datasetId\":" + notReady + ",\"purpose\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(authPost("/api/research/requests", researcher1.token(),
                "{\"datasetId\":99999,\"purpose\":\"x\"}")).andExpect(status().isNotFound());
        // neither a dataset nor a description
        mvc.perform(authPost("/api/research/requests", researcher1.token(), "{\"purpose\":\"x\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void adminSummaryCountsByStatus() throws Exception {
        create(5000);
        readyDataset();
        mvc.perform(authGet("/api/admin/datasets/summary", admin.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.ready").value(1))
                .andExpect(jsonPath("$.evaluating").value(1));
    }
}
