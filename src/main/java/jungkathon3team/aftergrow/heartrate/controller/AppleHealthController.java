package jungkathon3team.aftergrow.heartrate.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.heartrate.dto.AppleHealthDto;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateMeasurementResponse;
import jungkathon3team.aftergrow.heartrate.service.HeartRateMeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 애플 헬스(HealthKit) 연동.
 * <p>HealthKit은 온디바이스 API라 서버가 직접 읽거나 권한을 요청할 수 없다.
 * 명세의 GET 두 개를, 앱이 읽은 값과 동의 결과를 올리는 POST로 바꿨다.
 */
@Tag(name = "애플 헬스 연동", description = "워치 심박수 업로드 / 연동 상태 기록")

@RestController
@RequestMapping("/integrations/apple-health")
@RequiredArgsConstructor
public class AppleHealthController {

    private final HeartRateMeasurementService heartRateMeasurementService;

    @Operation(summary = "워치 심박수 업로드",
            description = "앱이 HealthKit에서 읽은 측정값을 러닝 세션에 기록합니다.")
    @PostMapping("/heart-rate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HeartRateMeasurementResponse> uploadHeartRate(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AppleHealthDto.HeartRateRequest request
    ) {
        return ApiResponse.ok(heartRateMeasurementService.uploadWatchMeasurement(userId, request));
    }

    @Operation(summary = "애플 헬스 연동 기록",
            description = "HealthKit 권한 동의 결과를 기록합니다. 권한 회수 시 false로도 호출됩니다.")
    @PostMapping("/link")
    public ApiResponse<AppleHealthDto.LinkResponse> link(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AppleHealthDto.LinkRequest request
    ) {
        return ApiResponse.ok(heartRateMeasurementService.linkAppleHealth(userId, request));
    }
}
