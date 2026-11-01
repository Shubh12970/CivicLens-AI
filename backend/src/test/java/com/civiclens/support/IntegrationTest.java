package com.civiclens.support;

import com.civiclens.ai.AnalysisModels.ProviderResult;
import com.civiclens.ai.AnalysisModels.RawAnalysis;
import com.civiclens.ai.IntelligenceProvider;
import com.civiclens.ai.ProviderException;
import com.civiclens.user.Role;
import com.civiclens.user.User;
import com.civiclens.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base class for integration tests: full Spring context, real PostgreSQL with Flyway migrations, MockMvc,
 * and a Mockito double for the Gemini provider (no network, no API key, no cost).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    public static final UUID PUNE = UUID.fromString("11111111-0000-0000-0000-000000000001");
    public static final UUID BENGALURU = UUID.fromString("11111111-0000-0000-0000-000000000002");
    public static final UUID NAGPUR = UUID.fromString("11111111-0000-0000-0000-000000000004");
    public static final UUID PUNE_ROADS = UUID.fromString("22222222-0001-0000-0000-000000000001");
    public static final UUID PUNE_ELECTRICAL = UUID.fromString("22222222-0001-0000-0000-000000000002");
    public static final UUID BLR_ROADS = UUID.fromString("22222222-0002-0000-0000-000000000001");
    public static final String PASSWORD = "Test-password-123";

    private static final AtomicInteger SEQ = new AtomicInteger();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", EmbeddedPostgresSupport::jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected PasswordEncoder encoder;

    @MockitoBean
    protected IntelligenceProvider provider;

    @BeforeEach
    void resetState() throws Exception {
        jdbc.execute("TRUNCATE idempotency_records, reward_transactions, department_reviews, comments, votes, "
                + "incident_analyses, incident_events, incidents, users CASCADE");
        when(provider.name()).thenReturn("gemini");
        when(provider.model()).thenReturn("test-model");
        when(provider.isConfigured()).thenReturn(true);
        when(provider.analyze(any())).thenReturn(aiResult("ROAD_DAMAGE", "HIGH", false, null));
    }

    protected static ProviderResult aiResult(String category, String severity, boolean safety, String departmentId) {
        return new ProviderResult(new RawAnalysis(category, severity, 0.82, safety,
                "The report describes a road defect that may cause a traffic hazard.",
                List.of("Two-wheelers swerving"), departmentId, List.of("Exact location"),
                "Verify the location and route for review."), "gemini", "test-model");
    }

    protected static ProviderException failure(ProviderException.FailureType type) {
        return new ProviderException(type, "simulated " + type);
    }

    // ---------- users ----------

    protected record Session(UUID userId, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected Session citizen() throws Exception {
        String email = "citizen" + SEQ.incrementAndGet() + "@example.test";
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "displayName", "Citizen " + SEQ.get()))))
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        return new Session(UUID.fromString(body.at("/user/id").asText()), body.get("accessToken").asText());
    }

    protected Session official(Role role, UUID departmentId) throws Exception {
        String email = role.name().toLowerCase() + SEQ.incrementAndGet() + "@example.test";
        users.save(new User(email, encoder.encode(PASSWORD), "Official " + SEQ.get(), role, departmentId, null, Instant.now()));
        return login(email);
    }

    protected Session deptAdmin(UUID departmentId) throws Exception {
        return official(Role.DEPARTMENT_ADMIN, departmentId);
    }

    protected Session systemAdmin() throws Exception {
        return official(Role.SYSTEM_ADMIN, null);
    }

    protected Session login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        return new Session(UUID.fromString(body.at("/user/id").asText()), body.get("accessToken").asText());
    }

    // ---------- requests ----------

    protected MockHttpServletRequestBuilder postJson(String url, Session session, Object body) throws Exception {
        MockHttpServletRequestBuilder b = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return session == null ? b : b.header("Authorization", session.bearer());
    }

    protected MockHttpServletRequestBuilder getAs(String url, Session session) {
        MockHttpServletRequestBuilder b = get(url);
        return session == null ? b : b.header("Authorization", session.bearer());
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected static Map<String, Object> report(UUID city, String title, String description, String locality,
                                                Double lat, Double lng, String category) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("cityId", city);
        m.put("title", title);
        m.put("description", description);
        m.put("locality", locality);
        m.put("latitude", lat);
        m.put("longitude", lng);
        m.put("category", category);
        return m;
    }

    protected static Map<String, Object> pothole() {
        return report(PUNE, "Large pothole near main market",
                "There is a large pothole near the main market and bikes are swerving to avoid it.",
                "Shivaji Nagar", 18.5308, 73.8475, "ROAD_DAMAGE");
    }

    /** Creates an incident and returns its id (fails the test if creation does not return 201). */
    protected UUID createIncident(Session session, Map<String, Object> report) throws Exception {
        MvcResult r = mvc.perform(postJson("/api/v1/incidents", session, report)).andReturn();
        if (r.getResponse().getStatus() != 201) {
            throw new AssertionError("Expected 201 but got " + r.getResponse().getStatus() + ": "
                    + r.getResponse().getContentAsString());
        }
        return UUID.fromString(body(r).get("id").asText());
    }
}
