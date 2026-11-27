package com.personal.medisynth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.personal.medisynth.DTO.RegistrationRequestResponse;
import com.personal.medisynth.DTO.RegistrationStatusResponse;
import com.personal.medisynth.model.RequestStatus;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.service.AdminService;
import com.personal.medisynth.service.RegistrationService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A second, completely independent application instance (new Spring context, new connection pool, no shared
 * memory) must see the pending request submitted through the first one. Both instances use the same PostgreSQL.
 */
class RestartPersistenceTest extends AbstractIntegrationTest {

    @Test
    void pendingRegistrationSurvivesAnApplicationRestart() throws Exception {
        String reference = registerDoctor("restart@medisynth.test", "MCI-RESTART");
        Actor admin = createAdmin();

        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(MediSynthApplication.class)
                .profiles("test")
                .run("--server.port=0", "--spring.main.banner-mode=off",
                        "--jwt.secret=" + JWT_SECRET, "--app.admin-bootstrap.secret=" + BOOTSTRAP_SECRET)) {

            RegistrationStatusResponse status = restarted.getBean(RegistrationService.class).getStatus(reference);
            assertThat(status.status()).isEqualTo(RequestStatus.PENDING_APPROVAL);

            var pending = restarted.getBean(AdminService.class).listRequests(RequestStatus.PENDING_APPROVAL, Role.DOCTOR, 0, 10);
            assertThat(pending.totalElements()).isEqualTo(1);
            RegistrationRequestResponse request = pending.content().get(0);
            assertThat(request.applicantEmail()).isEqualTo("restart@medisynth.test");
            assertThat(request.doctorProfile().licenseNumber()).isEqualTo("MCI-RESTART");

            // the restarted instance can finish the workflow
            restarted.getBean(RegistrationService.class).approve(requestIdFor("restart@medisynth.test"), admin.userId());
        }

        // ...and the original instance sees the outcome (same database, no stale in-memory state)
        assertThat(jdbc.queryForObject("SELECT account_status FROM users WHERE email = 'restart@medisynth.test'", String.class))
                .isEqualTo("ACTIVE");
        assertThat(login("restart@medisynth.test")).isNotBlank();
    }
}
