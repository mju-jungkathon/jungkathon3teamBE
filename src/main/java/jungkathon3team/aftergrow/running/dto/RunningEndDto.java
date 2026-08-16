package jungkathon3team.aftergrow.running.dto;

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

    public record Request(
            @NotNull LocalDateTime endedAt,
            @NotNull @Positive Integer durationSec,
            @NotNull @PositiveOrZero Double distanceKm,
            @NotNull Intensity intensity,

            /*
             * 러닝 중 수집한 GPS 트랙. 러닝 중에는 보내지 않고 종료 시점에 배열 통째로 한 번 보낸다.
             * 경로 없이 종료하는 클라이언트도 있으므로 선택 항목이다.
             * 상한은 클라이언트가 스로틀링(5~10초 간격)했을 때의 여유값 — 2시간 러닝도 1500점 남짓이다.
             */
            @Valid @Size(max = 10_000, message = "경로 점 개수는 10000개를 넘을 수 없습니다.")
            List<RoutePoint> routePath
    ) {}

    public record Response(
            UUID runningSessionId,
            RunningStatus status,
            String nextStep, // 항상 "HEART_RATE_CHECK" (화면 5로 이동)
            // 화면 5에서 기본으로 선택해 둘 측정 방식. 명세에 없는 추가 항목이며,
            // 최근 측정 이력에서 파생한다(별도 컬럼 없음).
            HeartRateSource defaultHeartRateSource
    ) {
        public static final String NEXT_STEP_HEART_RATE_CHECK = "HEART_RATE_CHECK";
    }
}
