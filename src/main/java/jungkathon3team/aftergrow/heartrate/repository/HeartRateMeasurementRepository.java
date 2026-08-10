package jungkathon3team.aftergrow.heartrate.repository;

import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface HeartRateMeasurementRepository extends JpaRepository<HeartRateMeasurement, UUID> {

    /** R2 홈: 사용자의 가장 최근 측정 1건 (모든 러닝 세션 통틀어). */
    Optional<HeartRateMeasurement> findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(UUID userId);

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
