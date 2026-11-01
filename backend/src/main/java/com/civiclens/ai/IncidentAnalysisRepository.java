package com.civiclens.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentAnalysisRepository extends JpaRepository<IncidentAnalysis, UUID> {

    Optional<IncidentAnalysis> findFirstByIncidentIdOrderByRunNumberDesc(UUID incidentId);

    List<IncidentAnalysis> findByIncidentIdOrderByRunNumberDesc(UUID incidentId);

    @Query("select coalesce(max(a.runNumber), 0) from IncidentAnalysis a where a.incidentId = :incidentId")
    int maxRunNumber(UUID incidentId);

    /** Latest run per incident, for list views (avoids N+1). */
    @Query("""
            select a from IncidentAnalysis a
            where a.incidentId in :incidentIds
              and a.runNumber = (select max(b.runNumber) from IncidentAnalysis b where b.incidentId = a.incidentId)
            """)
    List<IncidentAnalysis> findLatestFor(Collection<UUID> incidentIds);
}
