package jungkathon3team.aftergrow.recovery.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.recovery.dto.CooldownTimerStartResponse;
import jungkathon3team.aftergrow.recovery.service.RecoveryGuideService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "회복 가이드", description = "쿨다운 타이머")
@RestController
@RequestMapping("/recovery-guides")
@RequiredArgsConstructor
public class RecoveryGuideController {

    private final RecoveryGuideService recoveryGuideService;

    @Operation(summary = "쿨다운 타이머 시작",
            description = "회복 가이드 생성 직후 호출합니다. 실제 타이머는 클라이언트가 돌립니다.")
    @PostMapping("/{id}/cooldown-timer/start")
    public ApiResponse<CooldownTimerStartResponse> startCooldownTimer(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID recoveryGuideId
    ) {
        return ApiResponse.ok(recoveryGuideService.startCooldownTimer(userId, recoveryGuideId));
    }
}
