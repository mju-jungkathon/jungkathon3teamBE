package jungkathon3team.aftergrow.running.repository;

import jungkathon3team.aftergrow.running.entity.StretchingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface StretchingSessionRepository extends JpaRepository<StretchingSession, UUID> {

    /**
     * 러닝 기록 상세: 그 러닝 직전에 한 스트레칭을 찾는다.
     *
     * <p><b>두 테이블은 FK로 연결돼 있지 않다</b>({@code stretching_sessions}에는 {@code user_id}와
     * {@code started_at}만 있다). 스트레칭 세션이 러닝 세션보다 먼저 만들어지는 화면 흐름 때문이며,
     * 그래서 <b>시각 근접도로 추정</b>한다 — 러닝 시작 직전 일정 시간 안에 시작한 것 중 가장 최근 하나.
     */
    Optional<StretchingSession> findTopByUser_UserIdAndStartedAtBetweenOrderByStartedAtDesc(
            UUID userId, LocalDateTime from, LocalDateTime to);
}