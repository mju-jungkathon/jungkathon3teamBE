package jungkathon3team.aftergrow.heartrate.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** R4.5 POST /heart-rate-measurements/rppg/start */
public class RppgStartDto {

    @Schema(name = "RppgStartRequest")
    public record Request(
            @NotNull UUID runningSessionId
    ) {}

    @Schema(name = "RppgStartResponse")
    public record Response(
            UUID rppgSessionId,
            String status,
            int durationSec
    ) {
        public static final String STATUS_MEASURING = "MEASURING";
    }
}
