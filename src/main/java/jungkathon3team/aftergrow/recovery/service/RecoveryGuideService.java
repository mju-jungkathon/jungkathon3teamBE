package jungkathon3team.aftergrow.recovery.service;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.recovery.dto.CooldownTimerStartResponse;
import jungkathon3team.aftergrow.recovery.dto.NextRunSuggestionResponse;
import jungkathon3team.aftergrow.recovery.dto.RecoveryGuideResponse;
import jungkathon3team.aftergrow.recovery.dto.RunningCompleteResponse;
import jungkathon3team.aftergrow.recovery.entity.RecoveryGuide;
import jungkathon3team.aftergrow.recovery.external.RecoveryGuideAiClient;
import jungkathon3team.aftergrow.recovery.repository.RecoveryGuideRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.external.UvIndexClient.UvIndexResult;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import jungkathon3team.aftergrow.weather.dto.UvForecastResponse;
import jungkathon3team.aftergrow.weather.service.UvForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    private static final String NO_SUGGESTION_MESSAGE =
            "다음 러닝 추천 시간대를 계산할 수 없어요. 회복 완료 후 다시 확인해주세요.";
    private static final String SUGGESTION_REASON = "회복 완료 예상 시각 이후, UV 지수가 낮은 시간대";

    private final RecoveryGuideRepository recoveryGuideRepository;
    private final RunningSessionRepository runningSessionRepository;
    private final HeartRateMeasurementRepository heartRateMeasurementRepository;
    private final RecoveryGuideAiClient recoveryGuideAiClient;
    private final UvForecastService uvForecastService;

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
                        session.getDurationSec(),
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

    /**
     * 5.4 GET /recovery-guides/{id}/next-run-suggestion
     * <p>추천 시점 = 회복 완료 예상 시각(createdAt + cooldownTimerSec) 이후 & UV가 "낮음"(≤2, {@link UvIndexResult}
     * 재사용)인 첫 시간대. 위치 정보가 없거나, KMA 예보 호출이 실패하거나, 48시간(오늘+내일) 안에 맞는 시간대가
     * 없으면 셋 다 같은 안내 메시지로 degrade한다 — 원인별로 문구를 나누지 않기로 결정.
     */
    public NextRunSuggestionResponse getNextRunSuggestion(UUID userId, UUID recoveryGuideId) {
        RecoveryGuide guide = getOwnedGuide(userId, recoveryGuideId);
        RunningSession session = guide.getRunningSession();
        Double lat = session.getLat();
        Double lng = session.getLng();
        if (lat == null || lng == null) {
            return noSuggestion();
        }

        LocalDateTime recoveryCompleteAt = guide.getCreatedAt().plusSeconds(guide.getCooldownTimerSec());
        return findLowUvSlot(lat, lng, recoveryCompleteAt)
                .map(slot -> new NextRunSuggestionResponse(slot.time(), SUGGESTION_REASON, slot.uv()))
                .orElseGet(this::noSuggestion);
    }

    private Optional<LowUvSlot> findLowUvSlot(double lat, double lng, LocalDateTime after) {
        for (LocalDate date : List.of(after.toLocalDate(), after.toLocalDate().plusDays(1))) {
            UvForecastResponse forecast;
            try {
                forecast = uvForecastService.getForecast(lat, lng, date);
            } catch (BusinessException e) { // E5011 — 예보 실패는 못 찾은 것과 동일하게 degrade
                return Optional.empty();
            }

            Optional<LowUvSlot> match = forecast.hourly().stream()
                    .map(h -> new LowUvSlot(date.atTime(Integer.parseInt(h.hour()), 0), h.uv()))
                    .filter(slot -> !slot.time().isBefore(after))
                    .filter(slot -> "낮음".equals(UvIndexResult.of(slot.uv()).uvLevel()))
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private NextRunSuggestionResponse noSuggestion() {
        return new NextRunSuggestionResponse(null, NO_SUGGESTION_MESSAGE, null);
    }

    private record LowUvSlot(LocalDateTime time, int uv) {
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
