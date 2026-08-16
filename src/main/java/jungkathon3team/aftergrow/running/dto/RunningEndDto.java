package jungkathon3team.aftergrow.running.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.running.entity.Intensity;
import jungkathon3team.aftergrow.running.entity.RoutePoint;
import jungkathon3team.aftergrow.running.entity.RunningStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class RunningEndDto {

    @Schema(name = "RunningEndRequest",
            description = "3.5 러닝 종료 요청. 이미 끝난 세션에 다시 호출해도 에러 없이 현재 상태를 반환합니다(멱등).")
    public record Request(

            @Schema(description = "러닝을 종료한 시각", example = "2026-08-16T07:24:12",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDateTime endedAt,

            @Schema(description = "총 러닝 시간(초)", example = "1452",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Positive Integer durationSec,

            @Schema(description = "총 이동 거리(km)", example = "4.8",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @PositiveOrZero Double distanceKm,

            @Schema(description = "러닝 강도", example = "MODERATE",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Intensity intensity,

            /*
             * 러닝 중 수집한 GPS 트랙. 러닝 중에는 보내지 않고 종료 시점에 배열 통째로 한 번 보낸다.
             * 경로 없이 종료하는 클라이언트도 있으므로 선택 항목이다.
             * 상한은 클라이언트가 스로틀링(5~10초 간격)했을 때의 여유값 — 2시간 러닝도 1500점 남짓이다.
             */
            @Schema(description = "러닝 중 수집한 GPS 트랙. 선택 항목이지만 **지도를 쓸 거면 반드시 보내세요** — "
                    + "안 보내면 나중에 경로도 종료 지점도 복원할 수 없습니다. "
                    + "5~10초 간격으로 스로틀링하고, 종료 시점에 배열 통째로 한 번 보냅니다.")
            @Valid @Size(max = 10_000, message = "경로 점 개수는 10000개를 넘을 수 없습니다.")
            List<RoutePoint> routePath
    ) {}

    @Schema(name = "RunningEndResponse")
    public record Response(

            @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID runningSessionId,

            @Schema(example = "ENDED")
            RunningStatus status,

            @Schema(description = "다음에 이동할 화면. 항상 HEART_RATE_CHECK(화면 5)입니다.",
                    example = "HEART_RATE_CHECK")
            String nextStep, // 항상 "HEART_RATE_CHECK" (화면 5로 이동)

            // 화면 5에서 기본으로 선택해 둘 측정 방식. 명세에 없는 추가 항목이며,
            // 최근 측정 이력에서 파생한다(별도 컬럼 없음).
            @Schema(description = "화면 5에서 기본 선택해 둘 측정 방식. 최근 측정 이력에서 파생하며, "
                    + "이력이 없으면 애플 헬스 연동 여부에 따라 WATCH/RPPG입니다.", example = "RPPG")
            HeartRateSource defaultHeartRateSource
    ) {
        public static final String NEXT_STEP_HEART_RATE_CHECK = "HEART_RATE_CHECK";
    }
}
