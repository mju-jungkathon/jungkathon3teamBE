package jungkathon3team.aftergrow.recovery.service;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.recovery.dto.CooldownTimerStartResponse;
import jungkathon3team.aftergrow.recovery.dto.RecoveryGuideResponse;
import jungkathon3team.aftergrow.recovery.dto.RunningCompleteResponse;
import jungkathon3team.aftergrow.recovery.entity.RecoveryGuide;
import jungkathon3team.aftergrow.recovery.external.RecoveryGuideAiClient;
import jungkathon3team.aftergrow.recovery.repository.RecoveryGuideRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R5 AI 회복 가이드.
 * <p>남의 세션/가이드 접근은 404가 아니라 E4030이다 — 다른 R 서비스와 동일한 원칙
 * (존재 여부 자체를 노출하지 않기 위함).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecoveryGuideService {

    private final RecoveryGuideRepository recoveryGuideRepository;
    private final RunningSessionRepository runningSessionRepository;
    private final HeartRateMeasurementRepository heartRateMeasurementRepository;
    private final RecoveryGuideAiClient recoveryGuideAiClient;

    /**
     * 5.1 POST /running-sessions/{id}/recovery-guide
     * <p>running_session_id에 UNIQUE 제약이 있어(1:0..1) 세션당 가이드는 하나다.
     * 이미 생성된 가이드가 있으면 재생성하지 않고 기존 값을 그대로 반환한다(idempotent) —
     * 화면 재진입·중복 클릭으로 두 번 호출돼도 같은 가이드를 보게 하기 위함.
     */
    @Transactional
    public RecoveryGuideResponse generate(UUID userId, UUID sessionId) {
        RunningSession session = getOwnedSession(userId, sessionId);

        return recoveryGuideRepository.findByRunningSession_RunningSessionId(sessionId)
                .map(RecoveryGuideResponse::from)
                .orElseGet(() -> RecoveryGuideResponse.from(createGuide(session)));
    }

    private RecoveryGuide createGuide(RunningSession session) {
        Integer measuredBpm = heartRateMeasurementRepository
                .findTopByRunningSession_RunningSessionIdAndSyncStatusOrderByMeasuredAtDesc(
                        session.getRunningSessionId(), SyncStatus.SUCCESS)
                .map(m -> m.getAvgBpm())
                .orElse(null);

        RecoveryGuideAiClient.Guide guide = recoveryGuideAiClient.generate(
                new RecoveryGuideAiClient.Context(
                        session.getIntensity(),
                        session.getDistanceKm(),
                        session.getUvIndexAtStart(),
                        measuredBpm
                ));

        RecoveryGuide recoveryGuide = RecoveryGuide.create(
                session, measuredBpm, guide.summaryMessage(), guide.cooldownTimerSec());
        guide.actions().forEach(a -> recoveryGuide.addAction(a.type(), a.title(), a.description()));

        return recoveryGuideRepository.save(recoveryGuide);
    }

    /**
     * 5.2 POST /recovery-guides/{id}/cooldown-timer/start
     * <p>실제 타이머는 클라이언트가 로컬로 돌린다. 서버는 길이(cooldownTimerSec)를 확인해주고
     * 시작 시각을 내려줄 뿐 별도 상태를 저장하지 않는다.
     */
    public CooldownTimerStartResponse startCooldownTimer(UUID userId, UUID recoveryGuideId) {
        RecoveryGuide guide = getOwnedGuide(userId, recoveryGuideId);
        return new CooldownTimerStartResponse(guide.getCooldownTimerSec(), LocalDateTime.now());
    }

    /**
     * 5.3 POST /running-sessions/{id}/complete
     * <p>가이드가 아직 없으면(5.1을 건너뛴 경우) 404다 — 리포트로 보여줄 내용이 없다.
     */
    @Transactional
    public RunningCompleteResponse complete(UUID userId, UUID sessionId) {
        RunningSession session = getOwnedSession(userId, sessionId);
        RecoveryGuide guide = recoveryGuideRepository.findByRunningSession_RunningSessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040

        session.complete();

        return new RunningCompleteResponse(
                session.getRunningSessionId(), session.getStatus(), guide.getRecoveryGuideId());
    }

    private RecoveryGuide getOwnedGuide(UUID userId, UUID recoveryGuideId) {
        RecoveryGuide guide = recoveryGuideRepository.findById(recoveryGuideId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040
        if (!guide.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // E4030
        }
        return guide;
    }

    private RunningSession getOwnedSession(UUID userId, UUID sessionId) {
        RunningSession session = runningSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040
        if (!session.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // E4030
        }
        return session;
    }
}
