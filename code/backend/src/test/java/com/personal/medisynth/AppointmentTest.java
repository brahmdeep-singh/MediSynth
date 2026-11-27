package com.personal.medisynth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentTest extends AbstractIntegrationTest {

    private Actor admin;
    private Actor doctorA;
    private Actor doctorB;
    private Actor patient1;
    private Actor patient2;

    @BeforeEach
    void people() throws Exception {
        admin = createAdmin();
        doctorA = createVerifiedDoctor(admin, "docA@medisynth.test", "MCI-A");
        doctorB = createVerifiedDoctor(admin, "docB@medisynth.test", "MCI-B");
        patient1 = registerPatient("p1@medisynth.test");
        patient2 = registerPatient("p2@medisynth.test");
    }

    private long book(Actor patient, Actor doctor, String time) throws Exception {
        MvcResult r = mvc.perform(authPost("/api/appointments", patient.token(), bookingJson(doctor.profileId(), time, "Check-up")))
                .andExpect(status().isCreated()).andReturn();
        Number id = read(r, "$.id");
        return id.longValue();
    }

    // ------------------------------------------------------------------ persistence

    @Test
    void bookingIsPersistedForTheAuthenticatedPatient() throws Exception {
        String time = slot(3, 10, 0);
        mvc.perform(authPost("/api/appointments", patient1.token(), bookingJson(doctorA.profileId(), time, "Chest pain")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.patientId").value(patient1.userId()))
                .andExpect(jsonPath("$.doctorId").value(doctorA.profileId()))
                .andExpect(jsonPath("$.reason").value("Chest pain"));

        assertThat(count("SELECT count(*) FROM appointments WHERE patient_id = ? AND doctor_id = ? AND status = 'PENDING'",
                patient1.userId(), doctorA.profileId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'APPOINTMENT_CREATED'")).isEqualTo(1);

        mvc.perform(authGet("/api/appointments/my", patient1.token())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(authGet("/api/patients/me/appointments", patient1.token())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(authGet("/api/doctors/me/appointments", doctorA.token())).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void thePatientIdInTheBodyIsIgnored() throws Exception {
        mvc.perform(authPost("/api/appointments", patient1.token(), """
                {"doctorId":%d,"appointmentTime":"%s","reason":"x","patientId":%d,"status":"CONFIRMED"}"""
                .formatted(doctorA.profileId(), slot(3, 10, 0), patient2.userId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patient1.userId()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ------------------------------------------------------------------ double booking

    @Test
    void doubleBookingTheSameDoctorSlotIsRejected() throws Exception {
        String time = slot(3, 10, 30);
        long first = book(patient1, doctorA, time);

        mvc.perform(authPost("/api/appointments", patient2.token(), bookingJson(doctorA.profileId(), time, "Same slot")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
        assertThat(count("SELECT count(*) FROM appointments WHERE doctor_id = ?", doctorA.profileId())).isEqualTo(1);

        // a different doctor at the same time is fine, and so is the same doctor at another time
        Actor patient3 = registerPatient("p3@medisynth.test");
        book(patient3, doctorB, time);
        book(patient2, doctorA, slot(3, 11, 0));

        // a cancelled appointment frees the slot
        mvc.perform(authPost("/api/appointments/" + first + "/cancel", patient1.token(), "{\"reason\":\"clash\"}")).andExpect(status().isOk());
        mvc.perform(authPost("/api/appointments", patient2.token(), bookingJson(doctorA.profileId(), time, "Now free")))
                .andExpect(status().isCreated());
    }

    @Test
    void aPatientCannotHoldTwoAppointmentsAtTheSameTime() throws Exception {
        String time = slot(3, 12, 0);
        book(patient1, doctorA, time);
        mvc.perform(authPost("/api/appointments", patient1.token(), bookingJson(doctorB.profileId(), time, "Second")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PATIENT_SLOT_CONFLICT"));
    }

    @Test
    void concurrentBookingsOfOneSlotYieldExactlyOneAppointment() throws Exception {
        List<Actor> patients = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            patients.add(registerPatient("race" + i + "@medisynth.test"));
        }
        String time = slot(4, 9, 0);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (Actor p : patients) {
            tasks.add(() -> mvc.perform(authPost("/api/appointments", p.token(), bookingJson(doctorA.profileId(), time, "Race")))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = runConcurrently(tasks);

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM appointments WHERE doctor_id = ? AND status IN ('PENDING','CONFIRMED')",
                doctorA.profileId())).isEqualTo(1);
    }

    @Test
    void theDatabaseItselfRefusesADuplicateLiveSlot() throws Exception {
        long id = book(patient1, doctorA, slot(5, 14, 0));
        String insert = "INSERT INTO appointments (patient_id, doctor_id, start_time, reason, status, created_at, updated_at) "
                + "SELECT ?, doctor_id, start_time, 'raw insert', 'CONFIRMED', now(), now() FROM appointments WHERE id = ?";
        assertThatThrownBy(() -> jdbc.update(insert, patient2.userId(), id)).isInstanceOf(DataIntegrityViolationException.class);
        // a cancelled duplicate is allowed (partial index covers live statuses only)
        jdbc.update(insert.replace("'CONFIRMED'", "'CANCELLED'"), patient2.userId(), id);
        assertThat(count("SELECT count(*) FROM appointments")).isEqualTo(2);
    }

    // ------------------------------------------------------------------ booking rules

    @Test
    void onlyApprovedActiveDoctorsAcceptBookings() throws Exception {
        registerDoctor("pending@medisynth.test", "MCI-P");
        long pendingProfile = doctorProfileId("pending@medisynth.test");
        mvc.perform(authPost("/api/appointments", patient1.token(), bookingJson(pendingProfile, slot(3, 10, 0), "x")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DOCTOR_NOT_FOUND"));

        // an approved doctor who is later suspended stops accepting bookings
        mvc.perform(authPatch("/api/admin/users/" + doctorB.userId() + "/status", admin.token(), "{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        mvc.perform(authPost("/api/appointments", patient1.token(), bookingJson(doctorB.profileId(), slot(3, 10, 0), "x")))
                .andExpect(status().isNotFound());
        mvc.perform(authGet("/api/doctors", patient1.token())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(authGet("/api/doctors/" + pendingProfile, patient1.token())).andExpect(status().isNotFound());
        mvc.perform(authGet("/api/doctors/" + doctorA.profileId(), patient1.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.licenseNumber").doesNotExist());

        assertThat(count("SELECT count(*) FROM appointments")).isZero();
    }

    @Test
    void invalidBookingsAreRejected() throws Exception {
        mvc.perform(authPost("/api/appointments", patient1.token(), bookingJson(doctorA.profileId(), slot(3, 10, 15), "x")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SLOT"));
        mvc.perform(authPost("/api/appointments", patient1.token(), bookingJson(doctorA.profileId(), slot(-2, 10, 0), "x")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPOINTMENT_IN_PAST"));
        mvc.perform(authPost("/api/appointments", patient1.token(), bookingJson(doctorA.profileId(), slot(400, 10, 0), "x")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APPOINTMENT_TOO_FAR"));
        mvc.perform(authPost("/api/appointments", patient1.token(), bookingJson(doctorA.profileId(), slot(3, 10, 0), "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(authPost("/api/appointments", patient1.token(), "{\"doctorId\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(authPost("/api/appointments", patient1.token(), bookingJson(999999, slot(3, 10, 0), "x")))
                .andExpect(status().isNotFound());
        // only patients can book
        mvc.perform(authPost("/api/appointments", doctorA.token(), bookingJson(doctorB.profileId(), slot(3, 10, 0), "x")))
                .andExpect(status().isForbidden());
        mvc.perform(authPost("/api/appointments", admin.token(), bookingJson(doctorB.profileId(), slot(3, 10, 0), "x")))
                .andExpect(status().isForbidden());
        assertThat(count("SELECT count(*) FROM appointments")).isZero();
    }

    // ------------------------------------------------------------------ IDOR

    @Test
    void usersCannotReachAppointmentsThatAreNotTheirs() throws Exception {
        long id = book(patient1, doctorA, slot(3, 10, 0));

        // owner, assigned doctor and admin can read it
        mvc.perform(authGet("/api/appointments/" + id, patient1.token())).andExpect(status().isOk());
        mvc.perform(authGet("/api/appointments/" + id, doctorA.token())).andExpect(status().isOk());
        mvc.perform(authGet("/api/appointments/" + id, admin.token())).andExpect(status().isOk());

        // another patient and another doctor get a plain 404 (no hint that it exists)
        mvc.perform(authGet("/api/appointments/" + id, patient2.token())).andExpect(status().isNotFound());
        mvc.perform(authGet("/api/appointments/" + id, doctorB.token())).andExpect(status().isNotFound());

        // ...and cannot modify or cancel it either
        mvc.perform(authPatch("/api/appointments/" + id + "/status", patient2.token(), "{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(authPost("/api/appointments/" + id + "/cancel", patient2.token(), "{}")).andExpect(status().isNotFound());
        mvc.perform(authPatch("/api/appointments/" + id + "/status", doctorB.token(), "{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, id)).isEqualTo("PENDING");

        // list endpoints only ever contain the caller's own appointments
        mvc.perform(authGet("/api/patients/me/appointments", patient2.token())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(authGet("/api/appointments/my", patient2.token())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(authGet("/api/doctors/me/appointments", doctorB.token())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(authGet("/api/appointments/my", admin.token())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ status workflow

    @Test
    void statusRulesPerRole() throws Exception {
        long id = book(patient1, doctorA, slot(3, 10, 0));

        mvc.perform(authPatch("/api/appointments/" + id + "/status", patient1.token(), "{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_CANNOT_SET_STATUS"));
        mvc.perform(authPatch("/api/appointments/" + id + "/status", doctorA.token(), "{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        // a future appointment cannot be completed yet
        mvc.perform(authPatch("/api/appointments/" + id + "/status", doctorA.token(), "{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTED"));
        // invalid transition
        mvc.perform(authPatch("/api/appointments/" + id + "/status", doctorA.token(), "{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        mvc.perform(authPost("/api/appointments/" + id + "/cancel", patient1.token(), "{\"reason\":\"Feeling better\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Feeling better"));
        mvc.perform(authPost("/api/appointments/" + id + "/cancel", patient1.token(), "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, id)).isEqualTo("CANCELLED");
    }

    @Test
    void adminCanManageAppointmentsAndIsAudited() throws Exception {
        long id = book(patient1, doctorA, slot(3, 10, 0));

        mvc.perform(authGet("/api/admin/appointments?status=PENDING", admin.token())).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(authPatch("/api/appointments/" + id + "/status", admin.token(), "{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk());
        mvc.perform(authPost("/api/appointments/" + id + "/cancel", admin.token(), "{\"reason\":\"clinic closed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'APPOINTMENT_STATUS_CHANGED_BY_ADMIN' AND actor_id = ?",
                admin.userId())).isEqualTo(2);
    }

    @Test
    void appointmentsSurviveReloadingFromTheDatabase() throws Exception {
        long id = book(patient1, doctorA, slot(6, 16, 30));
        assertThat(jdbc.queryForObject("SELECT reason FROM appointments WHERE id = ?", String.class, id)).isEqualTo("Check-up");
        mvc.perform(authGet("/api/appointments/" + id, patient1.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorName").value("Dr Test")).andExpect(jsonPath("$.specialization").value("Cardiology"));
    }
}
