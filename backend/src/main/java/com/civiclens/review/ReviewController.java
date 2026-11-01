package com.civiclens.review;

import com.civiclens.common.ApiException;
import com.civiclens.common.PageParams;
import com.civiclens.common.PageResponse;
import com.civiclens.review.ReviewService.ReviewRequest;
import com.civiclens.review.ReviewService.ReviewView;
import com.civiclens.user.Authz;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @Operation(summary = "Review the department that resolved my report (once per incident)")
    @PostMapping("/api/v1/incidents/{incidentId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewView create(@PathVariable UUID incidentId, @Valid @RequestBody ReviewRequest request) {
        return reviews.create(Authz.require(), incidentId, request);
    }

    @Operation(summary = "The review of an incident (404 if none)")
    @GetMapping("/api/v1/incidents/{incidentId}/review")
    public ReviewView forIncident(@PathVariable UUID incidentId) {
        ReviewView review = reviews.forIncident(incidentId);
        if (review == null) {
            throw ApiException.notFound("Review");
        }
        return review;
    }

    @Operation(summary = "Reviews of a department, newest first")
    @GetMapping("/api/v1/departments/{departmentId}/reviews")
    public PageResponse<ReviewView> forDepartment(@PathVariable UUID departmentId,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        return reviews.forDepartment(departmentId, PageParams.page(page), PageParams.size(size, 20));
    }
}
