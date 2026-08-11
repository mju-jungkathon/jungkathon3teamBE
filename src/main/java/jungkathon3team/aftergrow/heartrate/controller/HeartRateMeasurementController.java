package jungkathon3team.aftergrow.heartrate.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateMeasurementResponse;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateRecordsResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgGuideResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgResultDto;
import jungkathon3team.aftergrow.heartrate.dto.RppgStartDto;
import jungkathon3team.aftergrow.heartrate.dto.RetryResponse;
import jungkathon3team.aftergrow.heartrate.service.HeartRateMeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "심박수", description = "rPPG 측정 / 측정 기록")

@RestController
@RequestMapping("/heart-rate-measurements")
@RequiredArgsConstructor
public class HeartRateMeasurementController {

    private final HeartRateMeasurementService heartRateMeasurementService;

    @Operation(summary = "rPPG 측정 안내",
            description = "화면 6 진입 시 측정 방법과 소요 시간을 안내합니다.")
    @GetMapping("/rppg/guide")
    public ApiResponse<RppgGuideResponse> rppgGuide() {
        return ApiResponse.ok(heartRateMeasurementService.rppgGuide());
    }

    @Operation(summary = "rPPG 측정 시작",
            description = "측정 세션을 발급합니다. 결과 제출 전까지 측정 기록은 생성되지 않습니다.")
    @PostMapping("/rppg/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RppgStartDto.Response> startRppg(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody RppgStartDto.Request request
    ) {
        return ApiResponse.ok(heartRateMeasurementService.startRppg(userId, request));
    }

    @Operation(summary = "rPPG 측정 결과 제출",
            description = "카메라 원본 영상이 아니라 온디바이스 알고리즘의 결과값만 받습니다. "
                    + "신호 품질이 POOR이면 재측정이 필요한 기록으로 저장됩니다.")
    @PostMapping("/rppg/{rppgSessionId}/result")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HeartRateMeasurementResponse> submitRppgResult(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID rppgSessionId,
            @Valid @RequestBody RppgResultDto.Request request
    ) {
        return ApiResponse.ok(
                heartRateMeasurementService.submitRppgResult(userId, rppgSessionId, request));
    }

    @Operation(summary = "측정 기록 목록",
            description = "range는 \"{일수}d\" 형식입니다(기본 30d).")
    @GetMapping
    public ApiResponse<HeartRateRecordsResponse> getRecords(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String range
    ) {
        return ApiResponse.ok(heartRateMeasurementService.getRecords(userId, range));
    }

    @Operation(summary = "실패 기록 재측정",
            description = "실패 기록은 삭제하지 않고, rPPG 측정 흐름으로 되돌아갈 정보를 반환합니다.")
    @PostMapping("/{id}/retry")
    public ApiResponse<RetryResponse> retry(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID measurementId
    ) {
        return ApiResponse.ok(heartRateMeasurementService.retry(userId, measurementId));
    }
}
