package jungkathon3team.aftergrow.running.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jungkathon3team.aftergrow.running.entity.RunningStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class RunningStartDto {

    public record Request(
            @NotNull LocalDateTime startedAt,
            @NotNull @Valid Location location,
            @NotNull Integer uvIndexAtStart
    ) {
        public record Location(
                @NotNull Double lat,
                @NotNull Double lng
        ) {}
    }

    public record Response(
            UUID runningSessionId,
            RunningStatus status
    ) {}
}
