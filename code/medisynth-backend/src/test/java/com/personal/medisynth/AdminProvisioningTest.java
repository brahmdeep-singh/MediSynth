package com.personal.medisynth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminProvisioningTest extends AbstractIntegrationTest {

    @Test
    void wrongSecretCannotCreateAnAdminAndTheAttemptIsAudited() throws Exception {
        mvc.perform(jsonPost("/api/auth/admin/bootstrap", adminJson("evil@medisynth.test"))
                .header("X-Bootstrap-Secret", "definitely-not-the-secret-0123456789abcdef"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PROVISIONING_FORBIDDEN"));

        assertThat(count("SELECT count(*) FROM users")).isZero();
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'ADMIN_BOOTSTRAP_FAILED'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM bootstrap_state WHERE consumed")).isZero();
        // audit rows never contain secrets or passwords
        String metadata = jdbc.queryForObject("SELECT coalesce(string_agg(metadata, ' '), '') FROM audit_logs", String.class);
        assertThat(metadata).doesNotContain(BOOTSTRAP_SECRET).doesNotContain(PASSWORD).doesNotContain("definitely-not");
    }

    @Test
    void missingSecretAndSecretInBodyOrUrlAreNotAccepted() throws Exception {
        mvc.perform(jsonPost("/api/auth/admin/bootstrap", adminJson("evil@medisynth.test")))
                .andExpect(status().isForbidden());
        mvc.perform(jsonPost("/api/auth/admin/bootstrap?secret=" + BOOTSTRAP_SECRET, adminJson("evil@medisynth.test")))
                .andExpect(status().isForbidden());
        mvc.perform(jsonPost("/api/auth/admin/bootstrap", """
                {"name":"Evil","email":"evil@medisynth.test","password":"%s","phone":"+919800000000","secret":"%s","role":"ADMIN"}"""
                .formatted(PASSWORD, BOOTSTRAP_SECRET))).andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM users")).isZero();
    }

    @Test
    void correctSecretPersistsAnActiveAdminWhoCanLogIn() throws Exception {
        mvc.perform(jsonPost("/api/auth/admin/bootstrap", adminJson(ADMIN_EMAIL)).header("X-Bootstrap-Secret", BOOTSTRAP_SECRET))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT granted_role FROM users WHERE email = ?", String.class, ADMIN_EMAIL)).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE email = ?", String.class, ADMIN_EMAIL)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, ADMIN_EMAIL)).startsWith("$2");
        assertThat(count("SELECT count(*) FROM bootstrap_state WHERE consumed")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'ADMIN_BOOTSTRAP_SUCCESS'")).isEqualTo(1);

        mvc.perform(jsonPost("/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(ADMIN_EMAIL, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("ADMIN"));
        mvc.perform(authGet("/api/admin/users", login(ADMIN_EMAIL))).andExpect(status().isOk());
    }

    @Test
    void theBootstrapCannotBeReusedEvenWithTheCorrectSecret() throws Exception {
        createAdmin();

        mvc.perform(jsonPost("/api/auth/admin/bootstrap", adminJson("second@medisynth.test")).header("X-Bootstrap-Secret", BOOTSTRAP_SECRET))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOTSTRAP_ALREADY_USED"));
        assertThat(count("SELECT count(*) FROM users WHERE granted_role = 'ADMIN'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'ADMIN_BOOTSTRAP_FAILED'")).isEqualTo(1);

        // even if the one-time flag were wiped, an existing admin keeps the endpoint closed
        jdbc.update("UPDATE bootstrap_state SET consumed = FALSE, consumed_at = NULL, consumed_by_user_id = NULL");
        mvc.perform(jsonPost("/api/auth/admin/bootstrap", adminJson("third@medisynth.test")).header("X-Bootstrap-Secret", BOOTSTRAP_SECRET))
                .andExpect(status().isConflict());
        assertThat(count("SELECT count(*) FROM users WHERE granted_role = 'ADMIN'")).isEqualTo(1);
    }

    @Test
    void invalidAdminDetailsDoNotBurnTheOneTimeBootstrap() throws Exception {
        mvc.perform(jsonPost("/api/auth/admin/bootstrap", """
                {"name":"Root","email":"root@medisynth.test","password":"weak","phone":"+919800000000"}""")
                .header("X-Bootstrap-Secret", BOOTSTRAP_SECRET)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        assertThat(count("SELECT count(*) FROM users")).isZero();
        assertThat(count("SELECT count(*) FROM bootstrap_state WHERE consumed")).isZero();
        createAdmin(); // still works afterwards
    }

    @Test
    void concurrentBootstrapRequestsCreateExactlyOneAdmin() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String email = "race" + i + "@medisynth.test";
            tasks.add(() -> mvc.perform(jsonPost("/api/auth/admin/bootstrap", adminJson(email))
                    .header("X-Bootstrap-Secret", BOOTSTRAP_SECRET)).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM users WHERE granted_role = 'ADMIN'")).isEqualTo(1);
    }

    @Test
    void onlyAnExistingAdminCanCreateFurtherAdmins() throws Exception {
        Actor admin = createAdmin();
        Actor patient = registerPatient("pat@medisynth.test");

        mvc.perform(jsonPost("/api/admin/users/admins", adminJson("new@medisynth.test"))).andExpect(status().isUnauthorized());
        mvc.perform(authPost("/api/admin/users/admins", patient.token(), adminJson("new@medisynth.test")))
                .andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM users WHERE granted_role = 'ADMIN'")).isEqualTo(1);

        mvc.perform(authPost("/api/admin/users/admins", admin.token(), adminJson("new@medisynth.test")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'ADMIN_CREATED_BY_ADMIN' AND actor_id = ?", admin.userId()))
                .isEqualTo(1);
        mvc.perform(authGet("/api/admin/users", login("new@medisynth.test"))).andExpect(status().isOk());
    }
}
