package jungkathon3team.aftergrow.running.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.running.dto.RunningEndDto;
import jungkathon3team.aftergrow.running.dto.RunningLiveResponse;
import jungkathon3team.aftergrow.running.dto.RunningPrepareResponse;
import jungkathon3team.aftergrow.running.dto.RunningStartDto;
import jungkathon3team.aftergrow.running.entity.Intensity;
import jungkathon3team.aftergrow.running.service.RunningSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "러닝", description = "준비 / 진행 중 / 종료")

@RestController
@RequestMapping("/running-sessions")
@RequiredArgsConstructor
public class RunningSessionController {

    private final RunningSessionService runningSessionService;

    @Operation(summary = "러닝 준비",
            description = "화면 3 진입 시 UV/위치/스트레칭을 안내합니다.")
    @GetMapping("/prepare")
    public ApiResponse<RunningPrepareResponse> prepare(
            @RequestParam double lat,
            @RequestParam double lng
    ) {
        return ApiResponse.ok(runningSessionService.prepare(lat, lng));
    }

    @Operation(summary = "러닝 시작")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RunningStartDto.Response> start(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody RunningStartDto.Request request
    ) {
        return ApiResponse.ok(runningSessionService.startRunning(userId, request));
    }

    @Operation(summary = "러닝 진행 중",
            description = "distanceKm/intensity는 클라이언트가 로컬 트래킹값을 함께 보낼 때만 사용합니다.")
    @GetMapping("/{id}/live")
    public ApiResponse<RunningLiveResponse> getLive(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID sessionId,
            @RequestParam(required = false) Double distanceKm,
            @RequestParam(required = false) Intensity intensity
    ) {
        return ApiResponse.ok(runningSessionService.getLive(userId, sessionId, distanceKm, intensity));
    }

    @Operation(summary = "러닝 종료",
            description = "러닝 종료 버튼 클릭 후 심박수 확인 페이지로 이동합니다.")
    @PostMapping("/{id}/end")
    public ApiResponse<RunningEndDto.Response> end(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody RunningEndDto.Request request
    ) {
        return ApiResponse.ok(runningSessionService.endRunning(userId, sessionId, request));
    }
}
