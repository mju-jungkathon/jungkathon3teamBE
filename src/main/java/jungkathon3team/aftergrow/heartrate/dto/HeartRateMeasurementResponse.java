package jungkathon3team.aftergrow.heartrate.dto;

import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;

import java.util.UUID;

/**
 * R4.2(워치 업로드)와 R4.6(rPPG 결과 제출)의 응답. 명세상 두 응답이 동일해 하나를 공유한다.
 * <p>신호 품질이 POOR이면 엔티티 단계에서 bpm/hrv가 null이 되어 그대로 내려간다.
 */
public record HeartRateMeasurementResponse(
        UUID heartRateMeasurementId,
        HeartRateSource heartRateSource,
        Integer avgBpm,
        Integer maxBpm,
        Integer hrvMs,
        SyncStatus syncStatus
) {
    public static HeartRateMeasurementResponse from(HeartRateMeasurement measurement) {
        return new HeartRateMeasurementResponse(
                measurement.getHeartRateMeasurementId(),
                measurement.getHeartRateSource(),
                measurement.getAvgBpm(),
                measurement.getMaxBpm(),
                measurement.getHrvMs(),
                measurement.getSyncStatus()
        );
    }
}
