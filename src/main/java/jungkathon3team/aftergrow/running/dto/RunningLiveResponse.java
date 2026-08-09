package jungkathon3team.aftergrow.running.dto;

import jungkathon3team.aftergrow.running.entity.Intensity;

import java.util.UUID;

public record RunningLiveResponse(
        UUID runningSessionId,
        long elapsedSec,
        Intensity intensity,
        Double distanceKm,
        String heartRateStatus, // 항상 "PENDING_AFTER_FINISH" (종료 후 확인)
        String stressStatus,    // 항상 "PENDING_HRV_CALCULATION" (심박변이도로 계산 예정)
        Integer uvIndex,
        String uvLevel
) {
    public static final String HEART_RATE_STATUS_PENDING = "PENDING_AFTER_FINISH";
    public static final String STRESS_STATUS_PENDING = "PENDING_HRV_CALCULATION";
}
