package jungkathon3team.aftergrow.running.repository;

import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.entity.RunningStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RunningSessionRepository extends JpaRepository<RunningSession, UUID> {

    boolean existsByUser_UserIdAndStatus(UUID userId, RunningStatus status);
}

