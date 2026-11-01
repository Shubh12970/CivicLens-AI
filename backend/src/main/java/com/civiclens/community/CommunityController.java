package com.civiclens.community;

import com.civiclens.common.PageParams;
import com.civiclens.common.PageResponse;
import com.civiclens.community.CommunityService.CommentRequest;
import com.civiclens.community.CommunityService.CommentView;
import com.civiclens.community.CommunityService.VoteRequest;
import com.civiclens.community.CommunityService.VoteSummary;
import com.civiclens.user.Authz;
import com.civiclens.user.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Community")
public class CommunityController {

    private final CommunityService community;

    public CommunityController(CommunityService community) {
        this.community = community;
    }

    @Operation(summary = "Set my vote (idempotent; one vote per user per incident)")
    @PutMapping("/api/v1/incidents/{incidentId}/vote")
    public VoteSummary vote(@PathVariable UUID incidentId, @Valid @RequestBody VoteRequest request) {
        return community.vote(Authz.require(), incidentId, request.type());
    }

    @Operation(summary = "Remove my vote (idempotent)")
    @DeleteMapping("/api/v1/incidents/{incidentId}/vote")
    public VoteSummary removeVote(@PathVariable UUID incidentId) {
        return community.removeVote(Authz.require(), incidentId);
    }

    @Operation(summary = "Vote totals (and my vote when authenticated)")
    @GetMapping("/api/v1/incidents/{incidentId}/votes")
    public VoteSummary votes(@PathVariable UUID incidentId) {
        return community.summary(incidentId, Authz.current().map(CurrentUser::id).orElse(null));
    }

    @Operation(summary = "Add a comment")
    @PostMapping("/api/v1/incidents/{incidentId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView addComment(@PathVariable UUID incidentId, @Valid @RequestBody CommentRequest request) {
        return community.addComment(Authz.require(), incidentId, request.body());
    }

    @Operation(summary = "List comments, oldest first")
    @GetMapping("/api/v1/incidents/{incidentId}/comments")
    public PageResponse<CommentView> comments(@PathVariable UUID incidentId,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return community.listComments(incidentId, PageParams.page(page), PageParams.size(size, 20),
                Authz.current().map(CurrentUser::id).orElse(null));
    }

    @Operation(summary = "Delete a comment (author or SYSTEM_ADMIN; soft delete)")
    @DeleteMapping("/api/v1/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID commentId) {
        community.deleteComment(Authz.require(), commentId);
    }
}
