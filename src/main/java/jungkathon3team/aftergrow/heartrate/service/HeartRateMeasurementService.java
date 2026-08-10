package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.dto.AppleHealthDto;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateMeasurementResponse;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateRecordsResponse;
import jungkathon3team.aftergrow.heartrate.dto.RetryResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgGuideResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgResultDto;
import jungkathon3team.aftergrow.heartrate.dto.RppgStartDto;
import jungkathon3team.aftergrow.heartrate.dto.SelectSourceDto;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.heartrate.repository.RppgSessionStore;
import jungkathon3team.aftergrow.profile.entity.IntegrationStatus;
import jungkathon3team.aftergrow.profile.repository.IntegrationStatusRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R4 심박수 측정 · R6 측정 기록.
 * <p>남의 러닝 세션/측정 기록 접근은 404가 아니라 E4030이다(소유 여부를 노출하지 않기 위함).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeartRateMeasurementService {

    static final String DEFAULT_RANGE = "30d";

    /** 지원 형식은 "{일수}d" 하나뿐이다. 주/월 단위는 명세에 없어 받지 않는다. */
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)d");

    private final HeartRateMeasurementRepository heartRateMeasurementRepository;
    private final RunningSessionRepository runningSessionRepository;
    private final IntegrationStatusRepository integrationStatusRepository;
    private final RppgSessionStore rppgSessionStore;

    /**
     * R6.1의 {@code range} 파라미터를 조회 하한 시각으로 바꾼다.
     * <p>순수 함수라 DB 없이 테스트한다. 생략/공백이면 기본 30일.
     */
    static LocalDateTime sinceOf(String range, LocalDateTime now) {
        String value = (range == null || range.isBlank()) ? DEFAULT_RANGE : range.trim();

        Matcher matcher = RANGE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST); // E4001
        }

        long days;
        try {
            days = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            // long을 넘는 자릿수. 그대로 두면 500이 되므로 잘못된 요청으로 돌린다.
            throw new BusinessException(ErrorCode.INVALID_REQUEST); // E4001
        }

        if (days <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST); // E4001
        }

        return now.minusDays(days);
    }

    /**
     * R6.1 GET /heart-rate-measurements?range=30d
     * <p>sourceRatio는 별도 집계 쿼리 없이 조회된 목록을 세서 만든다(30일치면 많아야 수십 건).
     */
    public HeartRateRecordsResponse getRecords(UUID userId, String range) {
        LocalDateTime since = sinceOf(range, LocalDateTime.now());

        List<HeartRateMeasurement> measurements = heartRateMeasurementRepository
                .findByRunningSession_User_UserIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDesc(userId, since);

        List<HeartRateRecordsResponse.Item> items = measurements.stream()
                .map(HeartRateRecordsResponse.Item::from)
                .toList();

        return new HeartRateRecordsResponse(items, sourceRatioOf(measurements));
    }

    /** rppgFailedCount는 rppg에서 빠지는 값이 아니라 부분집합이다. */
    private HeartRateRecordsResponse.SourceRatio sourceRatioOf(List<HeartRateMeasurement> measurements) {
        long watch = measurements.stream()
                .filter(m -> m.getHeartRateSource() == HeartRateSource.WATCH)
                .count();
        long rppg = measurements.stream()
                .filter(m -> m.getHeartRateSource() == HeartRateSource.RPPG)
                .count();
        long rppgFailed = measurements.stream()
                .filter(m -> m.getHeartRateSource() == HeartRateSource.RPPG)
                .filter(m -> m.getSyncStatus() == SyncStatus.FAILED)
                .count();

        return new HeartRateRecordsResponse.SourceRatio(watch, rppg, rppgFailed);
    }

    /** R4.4 GET /heart-rate-measurements/rppg/guide — 고정 안내 문구. */
    public RppgGuideResponse rppgGuide() {
        return RppgGuideResponse.defaults();
    }

    /**
     * R4.5 POST /heart-rate-measurements/rppg/start
     * <p>DB에는 아무것도 쓰지 않는다. 측정 중 매핑만 Redis에 남기고,
     * 측정 기록은 R4.6 결과 제출에서 처음 생성된다.
     */
    public RppgStartDto.Response startRppg(UUID userId, RppgStartDto.Request request) {
        RunningSession session = getOwnedSession(userId, request.runningSessionId());

        UUID rppgSessionId = UUID.randomUUID();
        rppgSessionStore.save(rppgSessionId, session.getRunningSessionId());

        return new RppgStartDto.Response(
                rppgSessionId,
                RppgStartDto.Response.STATUS_MEASURING,
                RppgGuideResponse.DURATION_SEC
        );
    }

    /**
     * R4.6 POST /heart-rate-measurements/rppg/{rppgSessionId}/result
     * <p>신호 품질이 POOR이면 FAILED로 저장된다(값을 버리는 판단은 엔티티가 한다).
     * 실패도 에러가 아니라 "재측정이 필요한 기록"이라 201로 응답한다.
     * <p>Redis 키는 제출 후 삭제해 같은 rppgSessionId로 두 번 제출할 수 없게 한다.
     */
    @Transactional
    public HeartRateMeasurementResponse submitRppgResult(UUID userId,
                                                         UUID rppgSessionId,
                                                         RppgResultDto.Request request) {
        UUID runningSessionId = rppgSessionStore.findRunningSessionId(rppgSessionId)
                // 만료됐거나 이미 제출됐거나 애초에 없던 id. 어느 세션의 것인지 알 수 없어 404다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040

        RunningSession session = getOwnedSession(userId, runningSessionId);

        HeartRateMeasurement measurement = heartRateMeasurementRepository.save(
                HeartRateMeasurement.rppg(
                        session,
                        request.avgBpm(),
                        request.maxBpm(),
                        request.hrvMs(),
                        request.measuredAt(),
                        request.signalQuality()
                ));

        rppgSessionStore.delete(rppgSessionId);

        return HeartRateMeasurementResponse.from(measurement);
    }

    /**
     * R6.2 POST /heart-rate-measurements/{id}/retry
     * <p>실패 기록을 삭제하지 않는다. 재측정 성공 행이 따로 쌓이고 실패 이력은 화면 8에 남는다.
     * <p>syncStatus가 FAILED가 아닌 기록에 호출해도 막지 않는다 — 명세에 전용 에러 코드가 없고,
     * 멀쩡한 측정을 다시 하겠다는 것을 거부할 이유가 없다.
     */
    public RetryResponse retry(UUID userId, UUID measurementId) {
        HeartRateMeasurement measurement = heartRateMeasurementRepository.findById(measurementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040

        RunningSession session = measurement.getRunningSession();
        if (!session.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // E4030
        }

        return new RetryResponse(
                RetryResponse.RETRY_FLOW_RPPG_GUIDE,
                session.getRunningSessionId()
        );
    }

    /**
     * R4.1 POST /running-sessions/{id}/heart-rate/select-source
     * <p>선택값을 저장하지 않는다 — 이후 흐름(R4.2/R4.6)이 각자 source를 확정하므로 읽는 곳이 없다.
     * 세션 소유자만 확인하고 다음 화면을 알려준다.
     */
    public SelectSourceDto.Response selectSource(UUID userId,
                                                 UUID sessionId,
                                                 SelectSourceDto.Request request) {
        getOwnedSession(userId, sessionId);

        String nextStep = request.heartRateSource() == HeartRateSource.WATCH
                ? SelectSourceDto.Response.NEXT_STEP_FETCH_APPLE_HEALTH
                : SelectSourceDto.Response.NEXT_STEP_RPPG_GUIDE;

        return new SelectSourceDto.Response(request.heartRateSource(), nextStep);
    }

    /**
     * R4.2 POST /integrations/apple-health/heart-rate
     * <p>명세는 서버가 애플 헬스를 조회하는 GET이지만, HealthKit은 온디바이스 API라 서버가 읽을 수 없다.
     * 앱이 읽은 값을 올리는 구조로 바꿨다. 앱은 읽기에 성공했을 때만 호출하므로 항상 SUCCESS다.
     */
    @Transactional
    public HeartRateMeasurementResponse uploadWatchMeasurement(UUID userId,
                                                               AppleHealthDto.HeartRateRequest request) {
        RunningSession session = getOwnedSession(userId, request.runningSessionId());

        HeartRateMeasurement measurement = heartRateMeasurementRepository.save(
                HeartRateMeasurement.watch(
                        session,
                        request.avgBpm(),
                        request.maxBpm(),
                        request.hrvMs(),
                        request.syncedAt()
                ));

        return HeartRateMeasurementResponse.from(measurement);
    }

    /**
     * R4.3 POST /integrations/apple-health/link
     * <p>명세의 authorize를 대체한다 — HealthKit 권한 동의는 OS 다이얼로그로 끝나므로
     * 서버가 돌려줄 authorizeUrl이 없다. 앱이 동의 결과를 알려오면 기록만 한다.
     * <p>사용자가 iOS 설정에서 권한을 회수하면 false로도 들어온다.
     */
    @Transactional
    public AppleHealthDto.LinkResponse linkAppleHealth(UUID userId, AppleHealthDto.LinkRequest request) {
        IntegrationStatus status = integrationStatusRepository.findById(userId)
                .orElseGet(() -> IntegrationStatus.of(userId));

        status.linkAppleHealth(request.linked());
        integrationStatusRepository.save(status);

        return new AppleHealthDto.LinkResponse(status.isAppleHealthLinked());
    }

    /**
     * 화면 5에서 기본으로 선택해 둘 측정 방식.
     * <p>명세에 없는 요구(최근 쓴 방식이 기본, 버튼으로 전환)를 위해 R3.5 /end 응답이 실어 보낸다.
     * 별도 컬럼 없이 측정 이력에서 파생하므로, 고르기만 하고 측정을 끝내지 않은 선택은 기억되지 않는다.
     */
    public HeartRateSource defaultSourceFor(UUID userId) {
        return heartRateMeasurementRepository
                .findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(userId)
                .map(HeartRateMeasurement::getHeartRateSource)
                .orElseGet(() -> integrationStatusRepository.findById(userId)
                        .filter(IntegrationStatus::isAppleHealthLinked)
                        .map(status -> HeartRateSource.WATCH)
                        .orElse(HeartRateSource.RPPG));
    }

    /**
     * 세션을 찾고 소유자를 확인한다.
     * <p>남의 세션은 404가 아니라 E4030이다 — 존재 여부 자체를 노출하지 않기 위함.
     */
    private RunningSession getOwnedSession(UUID userId, UUID sessionId) {
        RunningSession session = runningSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040
        if (!session.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // E4030
        }
        return session;
    }
}
