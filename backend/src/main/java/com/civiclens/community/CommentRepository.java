package com.civiclens.community;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Page<Comment> findByIncidentIdOrderByCreatedAtAscIdAsc(UUID incidentId, Pageable pageable);
}
