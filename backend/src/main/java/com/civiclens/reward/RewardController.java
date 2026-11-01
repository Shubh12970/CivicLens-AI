package com.civiclens.reward;

import com.civiclens.common.PageParams;
import com.civiclens.user.Authz;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@Tag(name = "Rewards")
public class RewardController {

    private final RewardService rewards;

    public RewardController(RewardService rewards) {
        this.rewards = rewards;
    }

    @Operation(summary = "Current user's reward balance and ledger (balance = sum of ledger entries)")
    @GetMapping("/api/v1/me/rewards")
    public RewardService.RewardSummary myRewards(@RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return rewards.summary(Authz.require().id(), PageParams.page(page), PageParams.size(size, 20));
    }

    public record RewardRule(RewardReason reason, int points) {
    }

    @Operation(summary = "The fixed reward rules")
    @GetMapping("/api/v1/system/reward-rules")
    public List<RewardRule> rules() {
        return Arrays.stream(RewardReason.values()).map(r -> new RewardRule(r, r.points())).toList();
    }
}
