package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateMeasurementResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgGuideResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgResultDto;
import jungkathon3team.aftergrow.heartrate.dto.RppgStartDto;
import jungkathon3team.aftergrow.heartrate.dto.RetryResponse;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.heartrate.repository.RppgSessionStore;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R4.4~4.6 rPPG 흐름과 R6.2 재측정.
 * <p>Redis는 @Transactional로 롤백되지 않으므로 발급한 rppgSessionId를 직접 지운다.
 */
@SpringBootTest
@Transactional
class HeartRateRppgFlowTest {

    @Autowired
    private HeartRateMeasurementService heartRateMeasurementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID userId;
    private RunningSession session;
    private final List<UUID> issuedRppgSessionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("rppg-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        userId = user.getUserId();
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    @AfterEach
    void tearDown() {
        issuedRppgSessionIds.forEach(id -> redisTemplate.delete(RppgSessionStore.KEY_PREFIX + id));
    }

    private UUID startRppg() {
        UUID id = heartRateMeasurementService
                .startRppg(userId, new RppgStartDto.Request(session.getRunningSessionId()))
                .rppgSessionId();
        issuedRppgSessionIds.add(id);
        return id;
    }

    private RppgResultDto.Request result(SignalQuality quality) {
        return new RppgResultDto.Request(146, 158, 38, LocalDateTime.now(), quality);
    }

    // --- 4.4 안내 ---

    @Test
    void rPPG_안내는_고정_문구와_측정_시간을_반환한다() {
        RppgGuideResponse guide = heartRateMeasurementService.rppgGuide();

        assertThat(guide.durationSec()).isEqualTo(12);
        assertThat(guide.instruction()).isNotBlank();
    }

    // --- 4.5 측정 시작 ---

    @Test
    void 측정을_시작하면_MEASURING_상태와_측정_시간을_반환한다() {
        RppgStartDto.Response response = heartRateMeasurementService
                .startRppg(userId, new RppgStartDto.Request(session.getRunningSessionId()));
        issuedRppgSessionIds.add(response.rppgSessionId());

        assertThat(response.rppgSessionId()).isNotNull();
        assertThat(response.status()).isEqualTo("MEASURING");
        assertThat(response.durationSec()).isEqualTo(RppgGuideResponse.DURATION_SEC);
    }

    /** 측정 중에는 DB에 행이 만들어지면 안 된다. 중단해도 미완성 기록이 남지 않아야 한다. */
    @Test
    void 측정_시작만으로는_측정_기록이_생기지_않는다() {
        startRppg();

        assertThat(heartRateMeasurementRepository
                .findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(userId))
                .isEmpty();
    }

    @Test
    void 없는_러닝_세션으로_측정을_시작하면_E4040이다() {
        assertThatThrownBy(() -> heartRateMeasurementService
                .startRppg(userId, new RppgStartDto.Request(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 남의_러닝_세션으로_측정을_시작하면_E4030이다() {
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("남")
                .build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService
                .startRppg(otherUserId, new RppgStartDto.Request(session.getRunningSessionId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // --- 4.6 결과 제출 ---

    @Test
    void 품질이_GOOD이면_측정값을_그대로_저장한다() {
        UUID rppgSessionId = startRppg();

        HeartRateMeasurementResponse response = heartRateMeasurementService
                .submitRppgResult(userId, rppgSessionId, result(SignalQuality.GOOD));

        assertThat(response.heartRateSource()).isEqualTo(HeartRateSource.RPPG);
        assertThat(response.syncStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(response.avgBpm()).isEqualTo(146);
        assertThat(response.heartRateMeasurementId()).isNotNull();
    }

    @Test
    void 품질이_POOR이면_FAILED로_저장하고_측정값을_버린다() {
        UUID rppgSessionId = startRppg();

        HeartRateMeasurementResponse response = heartRateMeasurementService
                .submitRppgResult(userId, rppgSessionId, result(SignalQuality.POOR));

        assertThat(response.syncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(response.avgBpm()).isNull();
        assertThat(response.maxBpm()).isNull();
        assertThat(response.hrvMs()).isNull();
    }

    @Test
    void 결과를_제출하면_측정_기록이_러닝_세션에_연결된다() {
        UUID rppgSessionId = startRppg();

        UUID measurementId = heartRateMeasurementService
                .submitRppgResult(userId, rppgSessionId, result(SignalQuality.GOOD))
                .heartRateMeasurementId();

        HeartRateMeasurement saved = heartRateMeasurementRepository.findById(measurementId).orElseThrow();
        assertThat(saved.getRunningSession().getRunningSessionId())
                .isEqualTo(session.getRunningSessionId());
    }

    /** 같은 rppgSessionId로 두 번 제출하면 측정 기록이 중복으로 쌓인다. */
    @Test
    void 같은_측정_세션으로_두_번_제출하면_E4040이다() {
        UUID rppgSessionId = startRppg();
        heartRateMeasurementService.submitRppgResult(userId, rppgSessionId, result(SignalQuality.GOOD));

        assertThatThrownBy(() -> heartRateMeasurementService
                .submitRppgResult(userId, rppgSessionId, result(SignalQuality.GOOD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 발급되지_않은_측정_세션으로_제출하면_E4040이다() {
        assertThatThrownBy(() -> heartRateMeasurementService
                .submitRppgResult(userId, UUID.randomUUID(), result(SignalQuality.GOOD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 남의_측정_세션에_결과를_제출하면_E4030이다() {
        UUID rppgSessionId = startRppg();
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("남")
                .build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService
                .submitRppgResult(otherUserId, rppgSessionId, result(SignalQuality.GOOD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // --- 6.2 재측정 ---

    @Test
    void 재측정은_rPPG_안내_흐름과_러닝_세션을_알려준다() {
        HeartRateMeasurement failed = heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 140, 150, 30, LocalDateTime.now(), SignalQuality.POOR));

        RetryResponse response = heartRateMeasurementService
                .retry(userId, failed.getHeartRateMeasurementId());

        assertThat(response.retryFlow()).isEqualTo("RPPG_GUIDE");
        assertThat(response.runningSessionId()).isEqualTo(session.getRunningSessionId());
    }

    /** 실패 이력은 화면 8에 남아야 한다. RUNNING_SESSIONS : MEASUREMENTS가 1:N인 이유다. */
    @Test
    void 재측정해도_실패_기록은_삭제되지_않는다() {
        HeartRateMeasurement failed = heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 140, 150, 30, LocalDateTime.now(), SignalQuality.POOR));

        heartRateMeasurementService.retry(userId, failed.getHeartRateMeasurementId());

        assertThat(heartRateMeasurementRepository.findById(failed.getHeartRateMeasurementId()))
                .isPresent();
    }

    @Test
    void 없는_측정_기록을_재측정하면_E4040이다() {
        assertThatThrownBy(() -> heartRateMeasurementService.retry(userId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 남의_측정_기록을_재측정하면_E4030이다() {
        HeartRateMeasurement mine = heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 140, 150, 30, LocalDateTime.now(), SignalQuality.POOR));
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("남")
                .build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService
                .retry(otherUserId, mine.getHeartRateMeasurementId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
