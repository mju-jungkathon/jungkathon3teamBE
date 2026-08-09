package jungkathon3team.aftergrow.running.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
            String nextStep // 항상 "HEART_RATE_CHECK" (화면 5로 이동)
    ) {
        public static final String NEXT_STEP_HEART_RATE_CHECK = "HEART_RATE_CHECK";
    }
}
