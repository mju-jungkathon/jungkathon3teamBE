package jungkathon3team.aftergrow.home.dto;

import java.time.LocalDateTime;

/**
 * R2 §2.1 GET /home 응답. (ApiResponse.data 내부에 담긴다)
 */
public record HomeResponse(
        String greeting,
        long weeklyRunCount,
        int weeklyGoalCount,
        int remainingToGoal,
        LatestMeasurement latestMeasurement,
        TodayRunningStatus todayRunningStatus,
        WeeklySummary weeklySummary
) {

    /** 홈 화면에 노출하는 오늘 러닝 상태. RunningStatus(IN_PROGRESS/ENDED/COMPLETED)를 매핑한 값. */
    public enum TodayRunningStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED
    }

    /** 가장 최근 심박수 측정. 측정 이력이 없으면 null. */
    public record LatestMeasurement(
            String heartRateSource,
            Integer avgBpm,
            LocalDateTime measuredAt
    ) {
    }

    /** 이번 주(월~일) 완료 세션 요약. */
    public record WeeklySummary(
            double totalDistanceKm,
            Integer avgBpm,
            String cumulativeUvLevel
    ) {
    }
}
