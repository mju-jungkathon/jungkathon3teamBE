package jungkathon3team.aftergrow.running.dto;

import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.running.entity.Intensity;
import jungkathon3team.aftergrow.running.entity.RoutePoint;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.entity.RunningStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GET /running-sessions/{id} — 러닝 기록 상세.
 *
 * <p>{@code routePath}가 여기에만 들어간다. 프론트는 이 배열을 카카오맵 폴리라인으로 그린다
 * (지도 렌더링은 전적으로 클라이언트 몫이고, 서버는 좌표만 돌려준다).
 * 경로 없이 종료한 세션은 {@code null}이므로 지도 대신 빈 상태를 보여줘야 한다.
 */
public record RunningSessionDetailResponse(
        UUID runningSessionId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Integer durationSec,
        Double distanceKm,
        Intensity intensity,
        RunningStatus status,
        Integer uvIndexAtStart,
        Location startLocation,
        List<RoutePoint> routePath,
        HeartRate heartRate
) {

    /** 러닝 시작 지점. 경로가 없어도 지도 중심을 잡을 수 있게 따로 내려준다. */
    public record Location(Double lat, Double lng) {
    }

    /** 해당 세션의 최근 성공 측정. 없으면 null. */
    public record HeartRate(
            HeartRateSource heartRateSource,
            Integer avgBpm,
            Integer maxBpm,
            Integer hrvMs,
            LocalDateTime measuredAt
    ) {
        public static HeartRate from(HeartRateMeasurement m) {
            return new HeartRate(
                    m.getHeartRateSource(),
                    m.getAvgBpm(),
                    m.getMaxBpm(),
                    m.getHrvMs(),
                    m.getMeasuredAt());
        }
    }

    public static RunningSessionDetailResponse of(RunningSession session, HeartRateMeasurement measurement) {
        return new RunningSessionDetailResponse(
                session.getRunningSessionId(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationSec(),
                session.getDistanceKm(),
                session.getIntensity(),
                session.getStatus(),
                session.getUvIndexAtStart(),
                new Location(session.getLat(), session.getLng()),
                session.getRoutePath(),
                measurement == null ? null : HeartRate.from(measurement)
        );
    }
}
