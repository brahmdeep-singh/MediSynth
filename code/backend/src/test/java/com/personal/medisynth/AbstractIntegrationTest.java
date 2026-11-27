package com.personal.medisynth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application against a real PostgreSQL test database and wipes its data before every test.
 * JWT and bootstrap secrets are random per run: nothing secret is committed.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Str0ngPassw0rd!";
    protected static final String JWT_SECRET = randomBase64(48);
    protected static final String BOOTSTRAP_SECRET = HexFormat.of().formatHex(randomBytes(32));
    protected static final String ADMIN_EMAIL = "admin@medisynth.test";

    /** Record of a logged-in test user. profileId is the doctor_profiles.id for doctors, else 0. */
    protected record Actor(String token, long userId, long profileId) {
    }

    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("app.admin-bootstrap.secret", () -> BOOTSTRAP_SECRET);
    }

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected DataSource dataSource;

    protected MockMvc mvc;
    protected JdbcTemplate jdbc;

    @BeforeEach
    void setUpBase() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc = new JdbcTemplate(dataSource);
        cleanDatabase();
    }

    /** DELETE (not TRUNCATE ... CASCADE) so the seeded bootstrap_state row survives. */
    protected void cleanDatabase() {
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM lab_test_requests");
        jdbc.update("DELETE FROM research_access_requests");
        jdbc.update("DELETE FROM synthetic_patient_records");
        jdbc.update("DELETE FROM synthetic_datasets");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM registration_requests");
        jdbc.update("DELETE FROM doctor_profiles");
        jdbc.update("DELETE FROM lab_technician_profiles");
        jdbc.update("DELETE FROM researcher_profiles");
        jdbc.update("DELETE FROM patient_profiles");
        jdbc.update("UPDATE bootstrap_state SET consumed = FALSE, consumed_at = NULL, consumed_by_user_id = NULL");
        jdbc.update("DELETE FROM users");
    }

    // ------------------------------------------------------------------ request helpers

    protected static MockHttpServletRequestBuilder jsonPost(String url, String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    protected static MockHttpServletRequestBuilder authGet(String url, String token) {
        return withToken(get(url), token);
    }

    protected static MockHttpServletRequestBuilder authPatch(String url, String token, String body) {
        return withToken(patch(url).contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    protected static MockHttpServletRequestBuilder authPost(String url, String token, String body) {
        return withToken(jsonPost(url, body), token);
    }

    protected static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    protected static <T> T read(MvcResult result, String jsonPath) throws Exception {
        return JsonPath.read(bodyOf(result), jsonPath);
    }

    // ------------------------------------------------------------------ JSON payloads

    protected static String patientJson(String email) {
        return """
                {"name":"Test Patient","email":"%s","password":"%s","phone":"+919876543210","role":"PATIENT"}"""
                .formatted(email, PASSWORD);
    }

    protected static String doctorJson(String email, String license) {
        return """
                {"name":"Dr Test","email":"%s","password":"%s","phone":"+919812345678","role":"DOCTOR",
                 "specialization":"Cardiology","qualifications":"MBBS, MD","experienceYears":8,
                 "licenseNumber":"%s","consultationFee":500.00,"availability":"Mon-Fri 09:00-17:00"}"""
                .formatted(email, PASSWORD, license);
    }

    protected static String labTechJson(String email, String employeeId) {
        return """
                {"name":"Lab Tech","email":"%s","password":"%s","phone":"+919811111111","role":"LAB_TECHNICIAN",
                 "department":"Pathology","employeeId":"%s"}""".formatted(email, PASSWORD, employeeId);
    }

    protected static String researcherJson(String email) {
        return """
                {"name":"Dr Researcher","email":"%s","password":"%s","phone":"+919822222222","role":"RESEARCHER",
                 "institution":"Northbridge Health Institute","researchArea":"Cardiac outcomes"}"""
                .formatted(email, PASSWORD);
    }

    protected static String adminJson(String email) {
        return """
                {"name":"Root Admin","email":"%s","password":"%s","phone":"+919800000000"}""".formatted(email, PASSWORD);
    }

    // ------------------------------------------------------------------ scenario helpers

    /** Registers a patient, returns the Actor (token from login). */
    protected Actor registerPatient(String email) throws Exception {
        mvc.perform(jsonPost("/api/auth/register", patientJson(email))).andExpect(status().isCreated());
        return new Actor(login(email), userId(email), 0);
    }

    /** Submits a doctor application. Returns the secret registration reference. */
    protected String registerDoctor(String email, String license) throws Exception {
        MvcResult r = mvc.perform(jsonPost("/api/auth/register", doctorJson(email, license)))
                .andExpect(status().isAccepted()).andReturn();
        return read(r, "$.registrationReference");
    }

    protected String login(String email) throws Exception {
        MvcResult r = mvc.perform(jsonPost("/api/auth/login",
                """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD))).andExpect(status().isOk()).andReturn();
        return read(r, "$.accessToken");
    }

    /** First-admin bootstrap through the real endpoint, then login. */
    protected Actor createAdmin() throws Exception {
        mvc.perform(jsonPost("/api/auth/admin/bootstrap", adminJson(ADMIN_EMAIL))
                .header("X-Bootstrap-Secret", BOOTSTRAP_SECRET)).andExpect(status().isCreated());
        return new Actor(login(ADMIN_EMAIL), userId(ADMIN_EMAIL), 0);
    }

    /** Doctor registers, admin approves, doctor logs in. */
    protected Actor createVerifiedDoctor(Actor admin, String email, String license) throws Exception {
        registerDoctor(email, license);
        mvc.perform(authPatch("/api/admin/registration-requests/" + requestIdFor(email) + "/approve", admin.token(), "{}"))
                .andExpect(status().isOk());
        return new Actor(login(email), userId(email), doctorProfileId(email));
    }

    /** Lab technician registers, admin approves, technician logs in. */
    protected Actor createLabTech(Actor admin, String email, String employeeId) throws Exception {
        mvc.perform(jsonPost("/api/auth/register", labTechJson(email, employeeId))).andExpect(status().isAccepted());
        mvc.perform(authPatch("/api/admin/registration-requests/" + requestIdFor(email) + "/approve", admin.token(), "{}"))
                .andExpect(status().isOk());
        return new Actor(login(email), userId(email), 0);
    }

    /** Researcher registers, admin approves, researcher logs in. */
    protected Actor createResearcher(Actor admin, String email) throws Exception {
        mvc.perform(jsonPost("/api/auth/register", researcherJson(email))).andExpect(status().isAccepted());
        mvc.perform(authPatch("/api/admin/registration-requests/" + requestIdFor(email) + "/approve", admin.token(), "{}"))
                .andExpect(status().isOk());
        return new Actor(login(email), userId(email), 0);
    }

    protected long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    protected long requestIdFor(String email) {
        return jdbc.queryForObject(
                "SELECT r.id FROM registration_requests r JOIN users u ON u.id = r.user_id WHERE u.email = ?",
                Long.class, email);
    }

    protected long doctorProfileId(String email) {
        return jdbc.queryForObject(
                "SELECT d.id FROM doctor_profiles d JOIN users u ON u.id = d.user_id WHERE u.email = ?", Long.class,
                email);
    }

    protected long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    /** A half-hour-aligned future slot, as an ISO-8601 UTC string. */
    protected static String slot(int daysAhead, int hour, int minute) {
        return Instant.now().plus(daysAhead, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
                .plus(hour, ChronoUnit.HOURS).plus(minute, ChronoUnit.MINUTES).toString();
    }

    protected static String bookingJson(long doctorId, String time, String reason) {
        return """
                {"doctorId":%d,"appointmentTime":"%s","reason":"%s"}""".formatted(doctorId, time, reason);
    }

    // ------------------------------------------------------------------ JWT forging (for negative tests)

    /** A token signed with the REAL secret, so only the claim under test decides the outcome. */
    protected static String forgeToken(long userId, Instant expiresAt, String issuer, String roleClaim) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET));
        return Jwts.builder().issuer(issuer).subject(String.valueOf(userId)).claim("role", roleClaim)
                .issuedAt(Date.from(expiresAt.minus(1, ChronoUnit.HOURS))).expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    // ------------------------------------------------------------------ concurrency

    /** Runs all tasks at (nearly) the same instant and returns their results in order. */
    protected static List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> f : futures) {
                results.add(f.get(90, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static String randomBase64(int n) {
        return Base64.getEncoder().encodeToString(randomBytes(n));
    }

    protected static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
