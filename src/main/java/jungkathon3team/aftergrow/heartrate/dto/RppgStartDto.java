package jungkathon3team.aftergrow.heartrate.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** R4.5 POST /heart-rate-measurements/rppg/start */
public class RppgStartDto {

    public record Request(
            @NotNull UUID runningSessionId
    ) {}

    public record Response(
            UUID rppgSessionId,
            String status,
            int durationSec
    ) {
        public static final String STATUS_MEASURING = "MEASURING";
    }
}
