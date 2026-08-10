package jungkathon3team.aftergrow.heartrate.repository;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;
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

/**
 * {@code HeartRateMeasurement.rppg(...)}가 POOR 신호에서 bpm을 null로 지우는 이유가
 * 바로 이 쿼리다 — avgBpmBetween은 sync_status를 거르지 않으므로, 값을 지우지 않으면
 * 실패한 측정까지 평균에 섞인다.
 */
@SpringBootTest
@Transactional
class HeartRateMeasurementRepositoryTest {

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    private UUID userId;
    private RunningSession session;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("hrrepo-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        userId = user.getUserId();
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    /** POOR 측정은 avgBpm이 null로 저장돼 avg() 집계에서 자동으로 빠진다 — sync_status 필터가 없어도 된다. */
    @Test
    void avgBpmBetween은_POOR_측정을_제외한_평균만_돌려준다() {
        LocalDateTime now = LocalDateTime.now();
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 150, 160, 40, now.minusHours(2), SignalQuality.GOOD));
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 140, 150, 30, now.minusHours(1), SignalQuality.POOR));

        Double avgBpm = heartRateMeasurementRepository
                .avgBpmBetween(userId, now.minusDays(1), now);

        assertThat(avgBpm).isEqualTo(150.0);
    }
}
