package jungkathon3team.aftergrow.heartrate.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;

/** R4.1 POST /running-sessions/{id}/heart-rate/select-source */
public class SelectSourceDto {

    @Schema(name = "SelectSourceRequest")
    public record Request(
            @NotNull HeartRateSource heartRateSource
    ) {}

    @Schema(name = "SelectSourceResponse")
    public record Response(
            HeartRateSource heartRateSource,
            String nextStep
    ) {
        public static final String NEXT_STEP_FETCH_APPLE_HEALTH = "FETCH_APPLE_HEALTH";
        public static final String NEXT_STEP_RPPG_GUIDE = "RPPG_GUIDE";
    }
}
