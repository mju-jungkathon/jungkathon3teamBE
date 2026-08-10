package jungkathon3team.aftergrow.heartrate.repository;

import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HeartRateMeasurementRepository extends JpaRepository<HeartRateMeasurement, UUID> {

    /** R2 홈: 사용자의 가장 최근 측정 1건 (모든 러닝 세션 통틀어). */
    Optional<HeartRateMeasurement> findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(UUID userId);

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
