package jungkathon3team.aftergrow.heartrate.repository;

import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HeartRateMeasurementRepository extends JpaRepository<HeartRateMeasurement, UUID> {

    /**
     * R4(defaultSourceFor 등): 사용자의 가장 최근 측정 1건 (모든 러닝 세션 통틀어), 성공/실패 가리지 않음.
     * <p>홈 대시보드처럼 "성공한 측정값"이 필요한 곳에는 쓰지 마라 — 실패 행이 avgBpm=null로 섞여 나온다.
     * 그런 곳은 {@link #findTopByRunningSession_User_UserIdAndSyncStatusOrderByMeasuredAtDesc}를 쓴다.
     */
    Optional<HeartRateMeasurement> findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(UUID userId);

    /** R2 홈: 사용자의 가장 최근 "성공" 측정 1건. 실패 측정은 건너뛰고 그 이전 성공 기록으로 폴백한다. */
    Optional<HeartRateMeasurement> findTopByRunningSession_User_UserIdAndSyncStatusOrderByMeasuredAtDesc(
            UUID userId, SyncStatus syncStatus);

    /**
     * R6.1 측정 기록 목록: 사용자의 range 내 측정 기록을 최신순으로.
     * <p>sourceRatio는 이 목록을 자바에서 세서 만든다(30일치면 많아야 수십 건).
     */
    List<HeartRateMeasurement> findByRunningSession_User_UserIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDesc(
            UUID userId, LocalDateTime since);

    /** R2 홈: 이번 주 평균 bpm. 측정이 없으면 null. */
    @Query("""
            select avg(m.avgBpm)
            from HeartRateMeasurement m
            where m.runningSession.user.userId = :userId
              and m.measuredAt between :start and :end
            """)
    Double avgBpmBetween(@Param("userId") UUID userId,
                         @Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end);
}
