package jungkathon3team.aftergrow.running.dto;

import jungkathon3team.aftergrow.running.entity.Intensity;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.entity.RunningStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GET /running-sessions?range=30d — 러닝 기록 목록(History 화면).
 *
 * <p><b>목록에는 {@code routePath}가 없다.</b> GPS 트랙은 세션당 수백 점이라 목록에 실으면
 * 응답이 수백 KB가 된다. 지도는 상세({@link RunningSessionDetailResponse})에서만 그린다.
 */
public record RunningRecordsResponse(
        List<Item> records,
        Summary summary
) {

    /** {@code Record}는 {@code java.lang.Record}를 가려서 쓰지 않는다(측정 기록 DTO와 같은 이유). */
    public record Item(
            UUID runningSessionId,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            Integer durationSec,
            Double distanceKm,
            Intensity intensity,
            RunningStatus status,
            Integer uvIndexAtStart,
            /** 해당 세션의 최근 성공 측정 평균 bpm. 측정이 없으면 null. */
            Integer avgBpm,
            /** 경로가 저장돼 있는지. 목록에서 "지도 보기" 노출 여부를 정하는 데 쓴다. */
            boolean hasRoutePath
    ) {
        public static Item of(RunningSession session, Integer avgBpm) {
            return new Item(
                    session.getRunningSessionId(),
                    session.getStartedAt(),
                    session.getEndedAt(),
                    session.getDurationSec(),
                    session.getDistanceKm(),
                    session.getIntensity(),
                    session.getStatus(),
                    session.getUvIndexAtStart(),
                    avgBpm,
                    session.getRoutePath() != null && !session.getRoutePath().isEmpty()
            );
        }
    }

    /**
     * 집계는 조회된 목록을 세서 만든다(30일치면 많아야 수십 건이라 별도 쿼리가 아깝다).
     * 홈 대시보드의 주간 집계와 달리 여기는 <b>range 전체</b> 기준이다.
     */
    public record Summary(
            int totalCount,
            double totalDistanceKm,
            int totalDurationSec
    ) {
    }
}
