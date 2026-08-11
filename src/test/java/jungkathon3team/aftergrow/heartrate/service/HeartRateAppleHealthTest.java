package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.dto.AppleHealthDto;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateMeasurementResponse;
import jungkathon3team.aftergrow.heartrate.dto.SelectSourceDto;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.profile.entity.IntegrationStatus;
import jungkathon3team.aftergrow.profile.repository.IntegrationStatusRepository;
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

/** R4.1 측정 방식 선택 · R4.2 워치 업로드 · R4.3 연동 기록 · 기본 측정 방식 파생. */
@SpringBootTest
@Transactional
class HeartRateAppleHealthTest {

    @Autowired
    private HeartRateMeasurementService heartRateMeasurementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    @Autowired
    private IntegrationStatusRepository integrationStatusRepository;

    private UUID userId;
    private RunningSession session;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("ah-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        userId = user.getUserId();
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    // --- 4.1 측정 방식 선택 ---

    @Test
    void 워치를_고르면_애플_헬스_조회로_분기한다() {
        SelectSourceDto.Response response = heartRateMeasurementService.selectSource(
                userId, session.getRunningSessionId(),
                new SelectSourceDto.Request(HeartRateSource.WATCH));

        assertThat(response.heartRateSource()).isEqualTo(HeartRateSource.WATCH);
        assertThat(response.nextStep()).isEqualTo("FETCH_APPLE_HEALTH");
    }

    @Test
    void rPPG를_고르면_측정_안내로_분기한다() {
        SelectSourceDto.Response response = heartRateMeasurementService.selectSource(
                userId, session.getRunningSessionId(),
                new SelectSourceDto.Request(HeartRateSource.RPPG));

        assertThat(response.nextStep()).isEqualTo("RPPG_GUIDE");
    }

    /** 선택값은 저장하지 않는다. 이후 흐름(4.2/4.6)이 각자 source를 확정한다. */
    @Test
    void 측정_방식을_골라도_측정_기록이_생기지_않는다() {
        heartRateMeasurementService.selectSource(userId, session.getRunningSessionId(),
                new SelectSourceDto.Request(HeartRateSource.RPPG));

        assertThat(heartRateMeasurementRepository
                .findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(userId))
                .isEmpty();
    }

    @Test
    void 남의_세션에서_측정_방식을_고르면_E4030이다() {
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed").nickname("남").build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService.selectSource(
                otherUserId, session.getRunningSessionId(),
                new SelectSourceDto.Request(HeartRateSource.RPPG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // --- 4.2 워치 데이터 업로드 ---

    @Test
    void 워치_데이터를_업로드하면_SUCCESS로_저장된다() {
        HeartRateMeasurementResponse response = heartRateMeasurementService.uploadWatchMeasurement(
                userId, new AppleHealthDto.HeartRateRequest(
                        session.getRunningSessionId(), 152, 168, 42,
                        LocalDateTime.of(2026, 8, 4, 6, 55)));

        assertThat(response.heartRateSource()).isEqualTo(HeartRateSource.WATCH);
        assertThat(response.syncStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(response.avgBpm()).isEqualTo(152);
        assertThat(response.maxBpm()).isEqualTo(168);
        assertThat(response.hrvMs()).isEqualTo(42);
    }

    @Test
    void 업로드한_syncedAt이_측정_시각으로_저장된다() {
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 4, 6, 55);

        UUID measurementId = heartRateMeasurementService.uploadWatchMeasurement(
                userId, new AppleHealthDto.HeartRateRequest(
                        session.getRunningSessionId(), 152, 168, 42, syncedAt))
                .heartRateMeasurementId();

        HeartRateMeasurement saved = heartRateMeasurementRepository.findById(measurementId).orElseThrow();
        assertThat(saved.getMeasuredAt()).isEqualTo(syncedAt);
        assertThat(saved.getSignalQuality()).isNull();
    }

    @Test
    void HRV가_없어도_업로드할_수_있다() {
        HeartRateMeasurementResponse response = heartRateMeasurementService.uploadWatchMeasurement(
                userId, new AppleHealthDto.HeartRateRequest(
                        session.getRunningSessionId(), 152, 168, null, LocalDateTime.now()));

        assertThat(response.hrvMs()).isNull();
        assertThat(response.syncStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void 남의_세션에_워치_데이터를_업로드하면_E4030이다() {
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed").nickname("남").build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService.uploadWatchMeasurement(
                otherUserId, new AppleHealthDto.HeartRateRequest(
                        session.getRunningSessionId(), 152, 168, 42, LocalDateTime.now())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // --- 4.3 연동 기록 ---

    @Test
    void 연동_정보가_없던_사용자도_연동을_기록할_수_있다() {
        AppleHealthDto.LinkResponse response = heartRateMeasurementService.linkAppleHealth(
                userId, new AppleHealthDto.LinkRequest(true));

        assertThat(response.appleHealthLinked()).isTrue();
        assertThat(integrationStatusRepository.findById(userId))
                .get().extracting(IntegrationStatus::isAppleHealthLinked).isEqualTo(true);
    }

    @Test
    void 연동을_해제하면_false로_갱신된다() {
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(true));

        AppleHealthDto.LinkResponse response = heartRateMeasurementService.linkAppleHealth(
                userId, new AppleHealthDto.LinkRequest(false));

        assertThat(response.appleHealthLinked()).isFalse();
    }

    @Test
    void 연동을_두_번_기록해도_행이_하나만_남는다() {
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(true));
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(true));

        assertThat(integrationStatusRepository.findById(userId)).isPresent();
    }

    // --- 기본 측정 방식 파생 ---

    @Test
    void 측정_이력이_있으면_가장_최근_방식이_기본이다() {
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, 152, 168, 42, LocalDateTime.now().minusDays(3)));
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 146, 158, 38, LocalDateTime.now().minusDays(1), SignalQuality.GOOD));

        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.RPPG);
    }

    /** 실패한 측정도 "그 방식을 골랐다"는 사실은 남는다. */
    @Test
    void 최근_측정이_실패했어도_그_방식이_기본이다() {
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, 152, 168, 42, LocalDateTime.now().minusDays(3)));
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 146, 158, 38, LocalDateTime.now().minusDays(1), SignalQuality.POOR));

        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.RPPG);
    }

    @Test
    void 이력이_없고_애플_헬스가_연동됐으면_워치가_기본이다() {
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(true));

        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.WATCH);
    }

    @Test
    void 이력이_없고_애플_헬스가_연동되지_않았으면_rPPG가_기본이다() {
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(false));

        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.RPPG);
    }

    /** 가입 직후라 integration_status 행 자체가 없는 사용자. */
    @Test
    void 연동_정보가_아예_없으면_rPPG가_기본이다() {
        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.RPPG);
    }
}
