package jungkathon3team.aftergrow.running.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.heartrate.dto.SelectSourceDto;
import jungkathon3team.aftergrow.heartrate.service.HeartRateMeasurementService;
import jungkathon3team.aftergrow.recovery.dto.RecoveryGuideResponse;
import jungkathon3team.aftergrow.recovery.dto.RunningCompleteResponse;
import jungkathon3team.aftergrow.recovery.service.RecoveryGuideService;
import jungkathon3team.aftergrow.running.dto.RunningEndDto;
import jungkathon3team.aftergrow.running.dto.RunningLiveResponse;
import jungkathon3team.aftergrow.running.dto.RunningPrepareResponse;
import jungkathon3team.aftergrow.running.dto.RunningSessionDetailResponse;
import jungkathon3team.aftergrow.running.dto.RunningRecordsResponse;
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
    private final HeartRateMeasurementService heartRateMeasurementService;
    private final RecoveryGuideService recoveryGuideService;

    @Operation(summary = "러닝 준비",
            description = "화면 3 진입 시 UV/위치/스트레칭을 안내합니다.")
    @GetMapping("/prepare")
    public ApiResponse<RunningPrepareResponse> prepare(
            @Parameter(description = "현재 위치 위도(브라우저 geolocation)", example = "37.5665")
            @RequestParam double lat,
            @Parameter(description = "현재 위치 경도", example = "126.9780")
            @RequestParam double lng
    ) {
        return ApiResponse.ok(runningSessionService.prepare(lat, lng));
    }

    @Operation(summary = "러닝 기록 목록",
            description = "range는 \"{일수}d\" 형식입니다(기본 30d). 응답 용량 때문에 목록에는 "
                    + "GPS 경로가 없습니다 — 경로는 상세 조회에서만 내려갑니다.")
    @GetMapping
    public ApiResponse<RunningRecordsResponse> getRecords(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "조회 기간. \"{일수}d\" 형식이며 생략하면 30d입니다.", example = "30d")
            @RequestParam(required = false) String range
    ) {
        return ApiResponse.ok(runningSessionService.getRecords(userId, range));
    }

    /**
     * {@code /prepare}가 먼저 선언돼 있지만 순서에 기대지 않는다 — 스프링은 구체적인 리터럴 경로를
     * {@code {id}} 템플릿보다 우선하고, {@code id}가 UUID라 "prepare"는 애초에 바인딩되지 않는다.
     * 그래도 회귀를 막으려고 테스트로 고정해 두었다.
     */
    @Operation(summary = "러닝 기록 상세",
            description = "지도에 그릴 GPS 경로(routePath)가 포함됩니다. 경로 없이 종료한 세션은 null입니다.")
    @GetMapping("/{id}")
    public ApiResponse<RunningSessionDetailResponse> getDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id
    ) {
        return ApiResponse.ok(runningSessionService.getDetail(userId, id));
    }

    @Operation(summary = "러닝 시작")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RunningStartDto.Response> start(
            @AuthenticationPrincipal UUID userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "서울 시청 출발", value = """
                            {
                              "startedAt": "2026-08-16T07:00:00",
                              "location": { "lat": 37.5665, "lng": 126.9780 },
                              "uvIndexAtStart": 5
                            }""")))
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
            @Parameter(description = "클라이언트가 로컬로 계산한 현재까지의 거리(km). 보내면 서버 값이 갱신됩니다.",
                    example = "2.5")
            @RequestParam(required = false) Double distanceKm,
            @Parameter(description = "현재 러닝 강도. 보내면 서버 값이 갱신됩니다.", example = "MODERATE")
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
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = {
                            @ExampleObject(name = "경로 포함(지도를 쓸 경우)", value = """
                                    {
                                      "endedAt": "2026-08-16T07:24:12",
                                      "durationSec": 1452,
                                      "distanceKm": 4.8,
                                      "intensity": "MODERATE",
                                      "routePath": [
                                        { "lat": 37.5665, "lng": 126.9780, "t": 0 },
                                        { "lat": 37.5672, "lng": 126.9791, "t": 8 },
                                        { "lat": 37.5688, "lng": 126.9812, "t": 17 }
                                      ]
                                    }"""),
                            @ExampleObject(name = "경로 없이 종료", value = """
                                    {
                                      "endedAt": "2026-08-16T07:24:12",
                                      "durationSec": 1452,
                                      "distanceKm": 4.8,
                                      "intensity": "MODERATE"
                                    }""")}))
            @Valid @RequestBody RunningEndDto.Request request
    ) {
        return ApiResponse.ok(runningSessionService.endRunning(userId, sessionId, request));
    }

    @Operation(summary = "심박수 측정 방식 선택",
            description = "화면 5에서 워치/rPPG 중 하나를 고르면 다음 화면을 알려줍니다. "
                    + "선택값은 저장하지 않습니다.")
    @PostMapping("/{id}/heart-rate/select-source")
    public ApiResponse<SelectSourceDto.Response> selectHeartRateSource(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody SelectSourceDto.Request request
    ) {
        return ApiResponse.ok(
                heartRateMeasurementService.selectSource(userId, sessionId, request));
    }

    @Operation(summary = "AI 회복 가이드 생성",
            description = "화면 7 진입 시 세션의 강도·거리·UV 지수·심박수 측정 결과를 종합해 회복 가이드를 생성합니다. "
                    + "이미 생성된 세션이면 재생성하지 않고 기존 가이드를 그대로 반환합니다.")
    @PostMapping("/{id}/recovery-guide")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecoveryGuideResponse> generateRecoveryGuide(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID sessionId
    ) {
        return ApiResponse.ok(recoveryGuideService.generate(userId, sessionId));
    }

    @Operation(summary = "세션 완료 & 리포트 확정",
            description = "화면 7 '완료하고 리포트 보기'. 회복 가이드가 먼저 생성되어 있어야 합니다.")
    @PostMapping("/{id}/complete")
    public ApiResponse<RunningCompleteResponse> complete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID sessionId
    ) {
        return ApiResponse.ok(recoveryGuideService.complete(userId, sessionId));
    }
}
