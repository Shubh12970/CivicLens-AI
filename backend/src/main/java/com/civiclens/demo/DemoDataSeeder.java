package com.civiclens.demo;

import com.civiclens.config.AppProperties;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentDtos.CreateIncidentRequest;
import com.civiclens.incident.IncidentDtos.DuplicateAcknowledgement;
import com.civiclens.incident.IncidentService;
import com.civiclens.user.CurrentUser;
import com.civiclens.user.Role;
import com.civiclens.user.User;
import com.civiclens.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Optional, clearly labelled demo data (DEMO_SEED_ENABLED=true and DEMO_SEED_PASSWORD set).
 * Demo incidents have {@code demoData=true} and titles starting with "[DEMO]"; they are never presented as real reports.
 * Runs once: skipped if any demo incident already exists.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    public static final UUID PUNE = UUID.fromString("11111111-0000-0000-0000-000000000001");
    public static final UUID PUNE_ROADS = UUID.fromString("22222222-0001-0000-0000-000000000001");
    public static final UUID PUNE_ELECTRICAL = UUID.fromString("22222222-0001-0000-0000-000000000002");

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final IncidentService incidents;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DemoDataSeeder(AppProperties props, UserRepository users, PasswordEncoder encoder, IncidentService incidents,
                          JdbcTemplate jdbc, Clock clock) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
        this.incidents = incidents;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        var demo = props.demo();
        if (demo == null || !demo.seedEnabled()) {
            return;
        }
        if (demo.seedPassword() == null || demo.seedPassword().length() < 12) {
            log.warn("demo-seed skipped: DEMO_SEED_PASSWORD must be set (at least 12 characters)");
            return;
        }
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM incidents WHERE demo_data", Integer.class);
        if (existing != null && existing > 0) {
            log.info("demo-seed skipped: demo incidents already exist");
            return;
        }
        User citizen = user("citizen.demo@civiclens.test", "Asha (demo)", Role.CITIZEN, null, demo.seedPassword());
        user("neighbour.demo@civiclens.test", "Ravi (demo)", Role.CITIZEN, null, demo.seedPassword());
        user("roads.officer.demo@civiclens.test", "Pune Roads Officer (demo)", Role.DEPARTMENT_ADMIN, PUNE_ROADS, demo.seedPassword());
        user("electrical.officer.demo@civiclens.test", "Pune Electrical Officer (demo)", Role.DEPARTMENT_ADMIN, PUNE_ELECTRICAL, demo.seedPassword());

        CurrentUser reporter = new CurrentUser(citizen.getId(), citizen.getDisplayName(), Role.CITIZEN, null, null);
        var ack = new DuplicateAcknowledgement(List.of(), true);
        List<CreateIncidentRequest> seeds = List.of(
                new CreateIncidentRequest(PUNE, "[DEMO] Large pothole near Shivaji Market",
                        "There is a large pothole near the main market entrance and bikes are swerving to avoid it.",
                        "Shivaji Nagar", "Opposite market gate 2", 18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE, false, ack),
                new CreateIncidentRequest(PUNE, "[DEMO] Streetlights off on FC Road lane",
                        "Four streetlights in the lane behind the college have been off for a week. The lane is very dark at night.",
                        "Deccan", null, 18.5196, 73.8410, IncidentCategory.STREETLIGHT, false, ack),
                new CreateIncidentRequest(PUNE, "[DEMO] Exposed wires at bus stop",
                        "Electrical wires are hanging out of the junction box next to the bus stop, close to where people wait.",
                        "Kothrud", "Kothrud depot bus stop", 18.5074, 73.8077, null, true, ack),
                new CreateIncidentRequest(PUNE, "[DEMO] Garbage pile not collected",
                        "Garbage has not been collected from the corner bin for five days and is spilling onto the road.",
                        "Aundh", null, 18.5590, 73.8070, IncidentCategory.GARBAGE, false, ack));
        for (CreateIncidentRequest seed : seeds) {
            incidents.create(reporter, seed, true);
        }
        log.info("demo-seed created {} demo incidents and 4 demo accounts (@civiclens.test)", seeds.size());
    }

    private User user(String email, String name, Role role, UUID departmentId, String password) {
        return users.findByEmailIgnoreCase(email).orElseGet(() ->
                users.save(new User(email, encoder.encode(password), name, role, departmentId, PUNE, clock.instant())));
    }
}
