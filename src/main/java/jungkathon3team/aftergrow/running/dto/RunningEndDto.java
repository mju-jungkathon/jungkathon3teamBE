package jungkathon3team.aftergrow.running.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.running.entity.Intensity;
import jungkathon3team.aftergrow.running.entity.RunningStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class RunningEndDto {

    public record Request(
            @NotNull LocalDateTime endedAt,
            @NotNull @Positive Integer durationSec,
            @NotNull @PositiveOrZero Double distanceKm,
            @NotNull Intensity intensity
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
