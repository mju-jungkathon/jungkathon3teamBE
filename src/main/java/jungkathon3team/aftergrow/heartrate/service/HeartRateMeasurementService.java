package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateRecordsResponse;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.heartrate.repository.RppgSessionStore;
import jungkathon3team.aftergrow.profile.repository.IntegrationStatusRepository;
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
}
