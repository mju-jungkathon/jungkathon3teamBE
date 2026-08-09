package jungkathon3team.aftergrow.running.repository;

import jungkathon3team.aftergrow.running.entity.StretchingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StretchingSessionRepository extends JpaRepository<StretchingSession, UUID> {
}