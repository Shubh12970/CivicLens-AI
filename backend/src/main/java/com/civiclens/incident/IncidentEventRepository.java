package com.civiclens.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, UUID> {

    List<IncidentEvent> findByIncidentIdOrderByCreatedAtAscIdAsc(UUID incidentId);

    long countByIncidentIdAndEventType(UUID incidentId, IncidentEvent.Type type);
}
