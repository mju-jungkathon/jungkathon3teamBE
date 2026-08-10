package jungkathon3team.aftergrow.heartrate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;

import java.time.LocalDateTime;

/**
 * R4.6 POST /heart-rate-measurements/rppg/{rppgSessionId}/result
 * <p>카메라 원본 영상은 서버로 보내지 않는다. 온디바이스 rPPG 알고리즘의 결과값만 올라온다.
 * <p>응답은 {@link HeartRateMeasurementResponse}를 쓴다.
 */
public class RppgResultDto {

    public record Request(
            @NotNull @Positive Integer avgBpm,
            @NotNull @Positive Integer maxBpm,
            Integer hrvMs,
            @NotNull LocalDateTime measuredAt,
            @NotNull SignalQuality signalQuality
    ) {}
}
