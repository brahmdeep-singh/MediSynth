package com.personal.medisynth;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Own Spring context (different properties) with a tiny login limit. Keep this to ONE test method. */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login.max=3",
        "app.rate-limit.login.window-seconds=60",
        "app.rate-limit.bootstrap.max=2",
        "app.rate-limit.bootstrap.window-seconds=60"})
class RateLimitTest extends AbstractIntegrationTest {

    @Test
    void repeatedLoginAndBootstrapAttemptsAreThrottled() throws Exception {
        String wrong = """
                {"email":"nobody@medisynth.test","password":"Wrong-password-1"}""";
        for (int i = 0; i < 3; i++) {
            mvc.perform(jsonPost("/api/auth/login", wrong)).andExpect(status().isUnauthorized());
        }
        mvc.perform(jsonPost("/api/auth/login", wrong))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // bootstrap guessing is throttled separately and still creates nothing
        for (int i = 0; i < 2; i++) {
            mvc.perform(jsonPost("/api/auth/admin/bootstrap", adminJson("evil@medisynth.test"))
                    .header("X-Bootstrap-Secret", "guess-" + i)).andExpect(status().isForbidden());
        }
        mvc.perform(jsonPost("/api/auth/admin/bootstrap", adminJson("evil@medisynth.test"))
                .header("X-Bootstrap-Secret", "guess-3")).andExpect(status().isTooManyRequests());
        assertThat(count("SELECT count(*) FROM users")).isZero();
    }
}
