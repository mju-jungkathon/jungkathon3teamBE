package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateRecordsResponse;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R4/R6 서비스 로직 테스트.
 * <p>range 파싱은 static 메서드라 컨텍스트 없이도 돌지만, sourceRatio 집계와 기본 source 파생은
 * 실제 DB 픽스처가 필요해 한 클래스에 함께 둔다.
 */
@SpringBootTest
@Transactional
class HeartRateMeasurementServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 12, 0);

    @Autowired
    private HeartRateMeasurementService heartRateMeasurementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    private UUID userId;
    private RunningSession session;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("hr-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        userId = user.getUserId();
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    /** daysAgo일 전에 측정된 기록을 만든다. */
    private void saveWatch(int daysAgo, int avgBpm) {
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, avgBpm, avgBpm + 15, 42, LocalDateTime.now().minusDays(daysAgo)));
    }

    private void saveRppg(int daysAgo, int avgBpm, SignalQuality quality) {
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, avgBpm, avgBpm + 12, 38, LocalDateTime.now().minusDays(daysAgo), quality));
    }

    // --- range 파싱 ---

    @Test
    void range가_30d면_30일_전부터_조회한다() {
        assertThat(HeartRateMeasurementService.sinceOf("30d", NOW))
                .isEqualTo(LocalDateTime.of(2026, 7, 11, 12, 0));
    }

    @Test
    void range가_7d면_7일_전부터_조회한다() {
        assertThat(HeartRateMeasurementService.sinceOf("7d", NOW))
                .isEqualTo(LocalDateTime.of(2026, 8, 3, 12, 0));
    }

    @Test
    void range를_생략하면_기본_30일이다() {
        assertThat(HeartRateMeasurementService.sinceOf(null, NOW))
                .isEqualTo(HeartRateMeasurementService.sinceOf("30d", NOW));
    }

    @Test
    void range가_빈_문자열이면_기본_30일이다() {
        assertThat(HeartRateMeasurementService.sinceOf("  ", NOW))
                .isEqualTo(HeartRateMeasurementService.sinceOf("30d", NOW));
    }

    @Test
    void 형식이_어긋난_range는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("abc", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 단위가_없는_range는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("30", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 주_단위처럼_지원하지_않는_단위는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("4w", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void _0일_조회는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("0d", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 음수_range는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("-5d", NOW))
                .isInstanceOf(BusinessException.class);
    }

    /** long 범위를 넘는 숫자에 NumberFormatException이 새어 나가면 500이 된다. */
    @Test
    void 지나치게_큰_숫자도_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("99999999999999999999d", NOW))
                .isInstanceOf(BusinessException.class);
    }

    // --- 6.1 목록 조회 ---

    @Test
    void 측정_기록이_없으면_빈_목록과_0_집계를_반환한다() {
        HeartRateRecordsResponse response = heartRateMeasurementService.getRecords(userId, "30d");

        assertThat(response.records()).isEmpty();
        assertThat(response.sourceRatio().watch()).isZero();
        assertThat(response.sourceRatio().rppg()).isZero();
        assertThat(response.sourceRatio().rppgFailedCount()).isZero();
    }

    @Test
    void 측정_기록을_최신순으로_반환한다() {
        saveWatch(5, 150);
        saveRppg(1, 146, SignalQuality.GOOD);
        saveWatch(10, 152);

        HeartRateRecordsResponse response = heartRateMeasurementService.getRecords(userId, "30d");

        assertThat(response.records())
                .extracting(HeartRateRecordsResponse.Item::measuredAt)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(response.records()).hasSize(3);
    }

    @Test
    void range_밖의_기록은_제외한다() {
        saveWatch(3, 150);
        saveWatch(40, 148);

        HeartRateRecordsResponse response = heartRateMeasurementService.getRecords(userId, "30d");

        assertThat(response.records()).hasSize(1);
        assertThat(response.sourceRatio().watch()).isEqualTo(1);
    }

    @Test
    void sourceRatio는_측정_방식별_건수를_센다() {
        saveWatch(1, 152);
        saveWatch(2, 150);
        saveRppg(3, 146, SignalQuality.GOOD);
        saveRppg(4, 140, SignalQuality.POOR);

        HeartRateRecordsResponse.SourceRatio ratio =
                heartRateMeasurementService.getRecords(userId, "30d").sourceRatio();

        assertThat(ratio.watch()).isEqualTo(2);
        assertThat(ratio.rppg()).isEqualTo(2);
        assertThat(ratio.rppgFailedCount()).isEqualTo(1);
    }

    /** rppgFailedCount는 rppg에서 따로 빠지는 게 아니라 부분집합이다. */
    @Test
    void 실패한_rPPG도_rppg_건수에_포함된다() {
        saveRppg(1, 140, SignalQuality.POOR);
        saveRppg(2, 141, SignalQuality.POOR);

        HeartRateRecordsResponse.SourceRatio ratio =
                heartRateMeasurementService.getRecords(userId, "30d").sourceRatio();

        assertThat(ratio.rppg()).isEqualTo(2);
        assertThat(ratio.rppgFailedCount()).isEqualTo(2);
        assertThat(ratio.watch()).isZero();
    }

    @Test
    void 실패한_기록은_avgBpm이_null로_내려간다() {
        saveRppg(1, 140, SignalQuality.POOR);

        HeartRateRecordsResponse.Item item =
                heartRateMeasurementService.getRecords(userId, "30d").records().get(0);

        assertThat(item.syncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(item.avgBpm()).isNull();
        assertThat(item.heartRateSource()).isEqualTo(HeartRateSource.RPPG);
        assertThat(item.runningSessionId()).isEqualTo(session.getRunningSessionId());
    }

    /** 남의 측정 기록이 섞이면 개인정보 유출이다. */
    @Test
    void 다른_사용자의_기록은_조회되지_않는다() {
        saveWatch(1, 152);
        User other = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("남")
                .build());

        HeartRateRecordsResponse response =
                heartRateMeasurementService.getRecords(other.getUserId(), "30d");

        assertThat(response.records()).isEmpty();
    }

    @Test
    void 잘못된_range로_목록을_조회하면_E4001이다() {
        assertThatThrownBy(() -> heartRateMeasurementService.getRecords(userId, "abc"))
                .isInstanceOf(BusinessException.class);
    }
}
