package jungkathon3team.aftergrow.recovery.service;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.recovery.dto.CooldownTimerStartResponse;
import jungkathon3team.aftergrow.recovery.dto.LowUvTimeRange;
import jungkathon3team.aftergrow.recovery.dto.NextRunSuggestionResponse;
import jungkathon3team.aftergrow.recovery.dto.RecoveryGuideResponse;
import jungkathon3team.aftergrow.recovery.dto.RunningCompleteResponse;
import jungkathon3team.aftergrow.recovery.entity.RecoveryGuide;
import jungkathon3team.aftergrow.recovery.external.RecoveryGuideAiClient;
import jungkathon3team.aftergrow.recovery.repository.RecoveryGuideRepository;
import jungkathon3team.aftergrow.running.entity.Intensity;
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
import java.util.ArrayList;
import java.util.List;
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
    private static final String SUGGESTION_REASON = "강도별 최소 휴식일 이후, UV 지수가 낮은 시간대";

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
     * <p>추천 시점 = 강도별 최소 휴식일({@link #restDaysFor}) 이후 & UV가 "낮음"(≤2, {@link UvIndexResult}
     * 재사용)인 시간대. 휴식일은 가이드가 생성된 날짜(세션이 끝난 날) 기준으로 세므로, 강도와 무관하게
     * 오늘은 항상 추천에서 제외된다 — {@code cooldownTimerSec}(2~10분)은 R5.2 타이머 UI 전용이라 이 계산에는
     * 쓰지 않는다. 낱개 시각이 아니라 <b>연속된 낮은-UV 시간대를 구간으로 묶어</b> 돌려준다
     * (예: 00,02,04,06시가 전부 낮음이면 하나의 00~06시 구간). 예보 격자가 항상 2시간 간격으로 빠짐없이
     * 채워져 있어(전날 22시 다음이 다음날 00시) 이틀치 배열을 이어붙이기만 하면 자정을 넘는 구간도 자동으로 이어진다.
     * <p>위치 정보가 없거나, KMA 예보 호출이 실패하거나, 탐색 창(휴식일 이후 48시간) 안에 맞는 시간대가 없으면
     * 셋 다 같은 안내 메시지 + 빈 배열로 degrade한다 — 원인별로 문구를 나누지 않기로 결정. KMA 예보는 발표시각
     * 기준 최대 75시간(~3.1일) 앞까지만 주므로({@code KmaUvForecastClient} 참고), HIGH(+3일)는 탐색 창 뒤쪽
     * 절반이 범위 밖이라 자주 이 degrade 경로를 탈 수 있다 — 의도된 동작이다.
     */
    public NextRunSuggestionResponse getNextRunSuggestion(UUID userId, UUID recoveryGuideId) {
        RecoveryGuide guide = getOwnedGuide(userId, recoveryGuideId);
        RunningSession session = guide.getRunningSession();
        Double lat = session.getLat();
        Double lng = session.getLng();
        if (lat == null || lng == null) {
            return noSuggestion();
        }

        LocalDateTime after = guide.getCreatedAt().toLocalDate()
                .plusDays(restDaysFor(session.getIntensity()))
                .atStartOfDay();
        List<LowUvTimeRange> ranges = findLowUvRanges(lat, lng, after);
        return ranges.isEmpty() ? noSuggestion() : new NextRunSuggestionResponse(ranges, SUGGESTION_REASON);
    }

    /** 강도가 높을수록 다음 러닝까지 더 쉬어야 한다는 팀 결정(LOW +1일/MODERATE +2일/HIGH +3일). */
    static int restDaysFor(Intensity intensity) {
        return switch (intensity) {
            case LOW -> 1;
            case MODERATE -> 2;
            case HIGH -> 3;
        };
    }

    private List<LowUvTimeRange> findLowUvRanges(double lat, double lng, LocalDateTime after) {
        List<UvSlot> slots = new ArrayList<>();
        for (LocalDate date : List.of(after.toLocalDate(), after.toLocalDate().plusDays(1))) {
            UvForecastResponse forecast;
            try {
                forecast = uvForecastService.getForecast(lat, lng, date);
            } catch (BusinessException e) { // E5011 — 예보 실패는 못 찾은 것과 동일하게 degrade
                return List.of();
            }
            forecast.hourly().forEach(h ->
                    slots.add(new UvSlot(date.atTime(Integer.parseInt(h.hour()), 0), h.uv())));
        }
        return groupLowUvRanges(slots, after);
    }

    /**
     * 시간순 슬롯을 훑으며 회복 완료 이후 & "낮음" 구간만 연속으로 이어붙인다.
     * <p>패키지 접근이라 {@code RecoveryGuideServiceTest}가 벽시계 시각과 무관하게 그룹핑 로직만 직접 검증한다.
     */
    static List<LowUvTimeRange> groupLowUvRanges(List<UvSlot> slots, LocalDateTime after) {
        List<LowUvTimeRange> ranges = new ArrayList<>();
        LocalDateTime rangeStart = null;
        LocalDateTime rangeEnd = null;
        int rangeMaxUv = 0;

        for (UvSlot slot : slots) {
            if (slot.time().isBefore(after)) {
                continue;
            }
            boolean low = "낮음".equals(UvIndexResult.of(slot.uv()).uvLevel());
            if (low) {
                if (rangeStart == null) {
                    rangeStart = slot.time();
                }
                rangeEnd = slot.time();
                rangeMaxUv = Math.max(rangeMaxUv, slot.uv());
            } else if (rangeStart != null) {
                ranges.add(new LowUvTimeRange(rangeStart, rangeEnd, rangeMaxUv));
                rangeStart = null;
            }
        }
        if (rangeStart != null) {
            ranges.add(new LowUvTimeRange(rangeStart, rangeEnd, rangeMaxUv));
        }
        return ranges;
    }

    private NextRunSuggestionResponse noSuggestion() {
        return new NextRunSuggestionResponse(List.of(), NO_SUGGESTION_MESSAGE);
    }

    record UvSlot(LocalDateTime time, int uv) {
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
