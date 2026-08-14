package jungkathon3team.aftergrow.recovery.repository;

import jungkathon3team.aftergrow.recovery.entity.RecoveryGuide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecoveryGuideRepository extends JpaRepository<RecoveryGuide, UUID> {

    /** R5.1 idempotency 체크 / R5.3 reportId 조회용. */
    Optional<RecoveryGuide> findByRunningSession_RunningSessionId(UUID runningSessionId);
}