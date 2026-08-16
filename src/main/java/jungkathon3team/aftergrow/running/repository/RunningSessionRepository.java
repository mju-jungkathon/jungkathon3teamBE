package jungkathon3team.aftergrow.running.repository;

import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.entity.RunningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RunningSessionRepository extends JpaRepository<RunningSession, UUID> {

    boolean existsByUser_UserIdAndStatus(UUID userId, RunningStatus status);

    /**
     * 러닝 기록 목록: 사용자의 range 내 세션을 최신순으로.
     * <p>진행 중(IN_PROGRESS) 세션도 함께 나온다 — 기록 화면에서 "달리는 중"으로 보여줄 수 있게.
     */
    List<RunningSession> findByUser_UserIdAndStartedAtGreaterThanEqualOrderByStartedAtDesc(
            UUID userId, LocalDateTime since);

    /** R2 홈: 특정 상태의 세션이 기간 내에 시작됐는지 (오늘 IN_PROGRESS 판별용). */
    boolean existsByUser_UserIdAndStatusAndStartedAtBetween(
            UUID userId, RunningStatus status, LocalDateTime start, LocalDateTime end);

    /** R2 홈: 여러 상태 중 하나라도 기간 내에 시작됐는지 (오늘 완료 판별용). */
    boolean existsByUser_UserIdAndStatusInAndStartedAtBetween(
            UUID userId, Collection<RunningStatus> statuses, LocalDateTime start, LocalDateTime end);

    /** R2 홈: 이번 주 완료 세션 수. */
    long countByUser_UserIdAndStatusInAndStartedAtBetween(
            UUID userId, Collection<RunningStatus> statuses, LocalDateTime start, LocalDateTime end);

    /** R2 홈: 이번 주 완료 세션 총거리(km). 없으면 0. */
    @Query("""
            select coalesce(sum(s.distanceKm), 0)
            from RunningSession s
            where s.user.userId = :userId
              and s.status in :statuses
              and s.startedAt between :start and :end
            """)
    double sumDistanceKmBetween(@Param("userId") UUID userId,
                                @Param("statuses") Collection<RunningStatus> statuses,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /** R2 홈: 이번 주 완료 세션의 평균 UV 지수. 없으면 null. */
    @Query("""
            select avg(s.uvIndexAtStart)
            from RunningSession s
            where s.user.userId = :userId
              and s.status in :statuses
              and s.startedAt between :start and :end
            """)
    Double avgUvIndexBetween(@Param("userId") UUID userId,
                             @Param("statuses") Collection<RunningStatus> statuses,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);
}

