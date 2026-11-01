package com.civiclens.incident;

import com.civiclens.ai.AnalysisService;
import com.civiclens.ai.AnalysisStatus;
import com.civiclens.ai.AnalysisView;
import com.civiclens.ai.IncidentAnalysis;
import com.civiclens.city.City;
import com.civiclens.city.CityDirectory;
import com.civiclens.city.CityRepository;
import com.civiclens.city.Department;
import com.civiclens.city.DepartmentRepository;
import com.civiclens.common.ApiException;
import com.civiclens.common.ErrorCode;
import com.civiclens.common.PageResponse;
import com.civiclens.community.VoteType;
import com.civiclens.duplicate.DuplicateDetectionService;
import com.civiclens.duplicate.DuplicateDtos.DuplicateCandidate;
import com.civiclens.duplicate.DuplicateDtos.DuplicateCheckRequest;
import com.civiclens.duplicate.DuplicateDtos.DuplicateCheckResponse;
import com.civiclens.duplicate.TextSimilarity;
import com.civiclens.incident.IncidentDtos.CreateIncidentRequest;
import com.civiclens.incident.IncidentDtos.FeedFilter;
import com.civiclens.incident.IncidentDtos.IncidentView;
import com.civiclens.incident.IncidentDtos.TimelineEntry;
import com.civiclens.user.CurrentUser;
import com.civiclens.workflow.StatusTransitionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidents;
    private final IncidentEventRepository events;
    private final IncidentQueryService queries;
    private final CityDirectory cityDirectory;
    private final CityRepository cityRepository;
    private final DepartmentRepository departmentRepository;
    private final DuplicateDetectionService duplicates;
    private final AnalysisService analysisService;
    private final StatusTransitionPolicy policy;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents, IncidentEventRepository events, IncidentQueryService queries,
                           CityDirectory cityDirectory, CityRepository cityRepository,
                           DepartmentRepository departmentRepository, DuplicateDetectionService duplicates,
                           AnalysisService analysisService, StatusTransitionPolicy policy, JdbcTemplate jdbc,
                           Clock clock) {
        this.incidents = incidents;
        this.events = events;
        this.queries = queries;
        this.cityDirectory = cityDirectory;
        this.cityRepository = cityRepository;
        this.departmentRepository = departmentRepository;
        this.duplicates = duplicates;
        this.analysisService = analysisService;
        this.policy = policy;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Creates a report. Persistence never depends on the AI provider: analysis is queued and runs after commit.
     * Creations in the same city are serialized with a transaction-scoped advisory lock so the duplicate re-check
     * below always sees reports committed by concurrent requests.
     */
    @Transactional
    public IncidentView create(CurrentUser reporter, CreateIncidentRequest req, boolean demoData) {
        City city = cityDirectory.requireActiveCity(req.cityId());
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw ApiException.validation("latitude", "latitude and longitude must be provided together.");
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", lockKey(city.getId()));

        DuplicateCheckResponse check = duplicates.check(new DuplicateCheckRequest(req.cityId(), req.title(),
                req.description(), req.locality(), req.latitude(), req.longitude(), req.category()));
        Set<UUID> reviewed = req.duplicateCheck() == null || req.duplicateCheck().reviewedCandidateIds() == null
                ? Set.of() : new HashSet<>(req.duplicateCheck().reviewedCandidateIds());
        boolean confirmed = req.duplicateCheck() != null && req.duplicateCheck().confirmedNotDuplicate();
        List<DuplicateCandidate> unreviewedStrong = check.candidates().stream()
                .filter(DuplicateCandidate::strongMatch)
                .filter(c -> !confirmed || !reviewed.contains(c.incidentId()))
                .toList();
        if (!unreviewedStrong.isEmpty()) {
            log.info("incident-create-blocked-possible-duplicate city={} candidates={}", city.getId(), unreviewedStrong.size());
            throw new ApiException(ErrorCode.POSSIBLE_DUPLICATE,
                    "Similar reports already exist. Support an existing report, or resubmit with "
                            + "duplicateCheck.confirmedNotDuplicate=true and the reviewed candidate ids.",
                    List.of(), check);
        }

        String normalized = TextSimilarity.normalize(DuplicateDetectionService.joinText(req.title(), req.description()));
        Incident incident = new Incident(city.getId(), reporter.id(), req.title().strip(), req.description().strip(),
                truncate(normalized, 2200), blankToNull(req.locality()), blankToNull(req.addressText()),
                req.latitude(), req.longitude(), req.category(), Boolean.TRUE.equals(req.hazardReported()),
                demoData, clock.instant());
        if (incident.getCategory() != IncidentCategory.OTHER) {
            incident.recommendFromMapping(cityDirectory.departmentFor(city.getId(), incident.getCategory()).orElse(null),
                    clock.instant());
        }
        incidents.saveAndFlush(incident);
        events.save(new IncidentEvent(incident.getId(), IncidentEvent.Type.CREATED, null, IncidentStatus.SUBMITTED,
                null, reporter.id(), reporter.role(), null, clock.instant()));
        analysisService.queue(incident.getId(), reporter.id());
        log.info("incident-created incidentId={} city={} category={} hazard={}", incident.getId(), city.getCode(),
                incident.getCategory(), incident.isHazardReported());
        return view(incident, reporter);
    }

    @Transactional(readOnly = true)
    public IncidentView get(UUID id, CurrentUser viewer) {
        return view(require(id), viewer);
    }

    public Incident require(UUID id) {
        return incidents.findById(id).orElseThrow(() -> ApiException.notFound("Incident"));
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentView> search(FeedFilter filter, CurrentUser viewer, int page, int size) {
        UUID viewerId = viewer == null ? null : viewer.id();
        IncidentQueryService.SearchResult result = queries.search(filter, viewerId, page, size);
        Map<UUID, Incident> byId = incidents.findAllById(result.ids()).stream()
                .collect(Collectors.toMap(Incident::getId, Function.identity()));
        List<Incident> ordered = result.ids().stream().map(byId::get).filter(Objects::nonNull).toList();
        List<IncidentView> views = assemble(ordered, result.stats(), viewer, false);
        return PageResponse.of(views, page, size, result.total());
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(UUID incidentId) {
        require(incidentId);
        List<IncidentEvent> list = events.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId);
        Map<UUID, String> deptNames = departmentNames(list.stream().map(IncidentEvent::getDepartmentId).toList());
        return list.stream().map(e -> new TimelineEntry(e.getId(), e.getEventType(), e.getFromStatus(), e.getToStatus(),
                e.getDepartmentId(), deptNames.get(e.getDepartmentId()), e.getActorRole(), e.getNote(), e.getCreatedAt()))
                .toList();
    }

    /** Single incident with detail-only fields (latest analysis, actions available to the viewer). */
    @Transactional(readOnly = true)
    public IncidentView view(Incident incident, CurrentUser viewer) {
        Map<UUID, IncidentQueryService.Stats> stats = queries.statsFor(List.of(incident.getId()));
        return assemble(List.of(incident), stats, viewer, true).get(0);
    }

    private List<IncidentView> assemble(List<Incident> list, Map<UUID, IncidentQueryService.Stats> stats,
                                        CurrentUser viewer, boolean detail) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Incident::getId).toList();
        Map<UUID, String> cityNames = cityRepository.findAllById(list.stream().map(Incident::getCityId).distinct().toList())
                .stream().collect(Collectors.toMap(City::getId, City::getName));
        Map<UUID, String> deptNames = departmentNames(list.stream()
                .flatMap(i -> Stream.of(i.getDepartmentId(), i.getRecommendedDepartmentId())).toList());
        Map<UUID, IncidentAnalysis> analyses = analysisService.latestFor(ids);
        UUID viewerId = viewer == null ? null : viewer.id();
        Map<UUID, Integer> myVotes = queries.votesBy(viewerId, ids);

        return list.stream().map(i -> {
            IncidentQueryService.Stats s = stats.getOrDefault(i.getId(),
                    new IncidentQueryService.Stats(0, 0, 0, 0, 0, null));
            IncidentAnalysis a = analyses.get(i.getId());
            AnalysisStatus analysisStatus = a == null ? null : a.getStatus();
            AnalysisView analysisView = detail && a != null ? analysisService.toView(a) : null;
            List<String> actions = detail ? policy.availableActions(viewer, i, clock.instant()) : null;
            return new IncidentView(i.getId(), i.getTitle(), i.getDescription(), i.getCityId(), cityNames.get(i.getCityId()),
                    i.getLocality(), i.getAddressText(), i.getLatitude(), i.getLongitude(), i.getCategory(),
                    i.getCategorySource(), i.getSeverity(), i.getSeveritySource(), i.isSafetyCritical(),
                    i.isHazardReported(), i.getStatus(), i.getDepartmentId(), deptNames.get(i.getDepartmentId()),
                    i.getRecommendedDepartmentId(), deptNames.get(i.getRecommendedDepartmentId()),
                    i.getRecommendedDepartmentSource(), analysisStatus, s.up(), s.down(), s.up() - s.down(),
                    s.comments(), s.priority(), s.trending(), s.distanceMeters(), i.getDuplicateOfId(),
                    i.getResolutionNote(), i.isDemoData(), viewerId != null && viewerId.equals(i.getReporterId()),
                    VoteType.fromValue(myVotes.get(i.getId())), i.getCreatedAt(), i.getUpdatedAt(),
                    i.getStatusChangedAt(), i.getResolvedAt(), i.getVersion(), analysisView, actions);
        }).toList();
    }

    private Map<UUID, String> departmentNames(Collection<UUID> ids) {
        List<UUID> nonNull = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (nonNull.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllById(nonNull).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
    }

    static long lockKey(UUID cityId) {
        return cityId.getMostSignificantBits() ^ cityId.getLeastSignificantBits();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
