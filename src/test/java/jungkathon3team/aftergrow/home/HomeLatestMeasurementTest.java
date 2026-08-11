package jungkathon3team.aftergrow.home;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /home의 latestMeasurement가 실패한 측정을 건너뛰고 성공한 측정으로 폴백하는지 확인한다.
 * <p>필터 없이 조회하면 최근 실패 측정(avgBpm=null)이 그대로 노출되어 "측정 없음"과 구분이 안 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeLatestMeasurementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String bearer;
    private RunningSession session;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("home-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    @Test
    void 가장_최근_측정이_실패했으면_그_이전_성공한_측정으로_폴백한다() throws Exception {
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, 150, 160, 40, LocalDateTime.now().minusDays(2)));
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 140, 150, 30, LocalDateTime.now().minusHours(1), SignalQuality.POOR));

        mockMvc.perform(get("/home").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestMeasurement.heartRateSource").value("WATCH"))
                .andExpect(jsonPath("$.data.latestMeasurement.avgBpm").value(150));
    }

    @Test
    void 성공한_측정이_하나도_없으면_latestMeasurement는_null이다() throws Exception {
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 140, 150, 30, LocalDateTime.now(), SignalQuality.POOR));

        mockMvc.perform(get("/home").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestMeasurement").doesNotExist());
    }
}
