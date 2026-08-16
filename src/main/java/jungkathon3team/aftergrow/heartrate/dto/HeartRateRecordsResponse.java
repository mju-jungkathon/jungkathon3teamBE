package jungkathon3team.aftergrow.heartrate.dto;

import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import io.swagger.v3.oas.annotations.media.Schema;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * R6.1 GET /heart-rate-measurements?range=30d
 * <p>명세 예시의 sourceRatio는 records 건수와 맞지 않는다(예시가 잘린 것으로 본다).
 * 같은 range 안의 실제 건수로 정의하며, rppgFailedCount는 rppg의 부분집합이다.
 */
public record HeartRateRecordsResponse(
        List<Item> records,
        SourceRatio sourceRatio
) {
    /** {@code Record}라는 이름은 {@code java.lang.Record}를 가려서 쓰지 않는다. */
    @Schema(name = "HeartRateRecordItem")
    public record Item(
            UUID heartRateMeasurementId,
            LocalDateTime measuredAt,
            HeartRateSource heartRateSource,
            Integer avgBpm,
            UUID runningSessionId,
            SyncStatus syncStatus
    ) {
        public static Item from(HeartRateMeasurement measurement) {
            return new Item(
                    measurement.getHeartRateMeasurementId(),
                    measurement.getMeasuredAt(),
                    measurement.getHeartRateSource(),
                    measurement.getAvgBpm(),
                    measurement.getRunningSession().getRunningSessionId(),
                    measurement.getSyncStatus()
            );
        }
    }

    public record SourceRatio(
            long watch,
            long rppg,
            long rppgFailedCount
    ) {}
}
