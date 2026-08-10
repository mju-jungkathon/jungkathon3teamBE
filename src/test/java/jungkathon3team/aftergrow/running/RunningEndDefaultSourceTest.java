package jungkathon3team.aftergrow.running;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 러닝 종료 응답이 화면 5의 기본 측정 방식을 함께 내려주는지 확인한다.
 * <p>명세에 없는 신규 요구라, 별도 엔드포인트 없이 /end 응답에 얹었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RunningEndDefaultSourceTest {

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
                .email("end-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    private org.springframework.test.web.servlet.ResultActions endRunning() throws Exception {
        return mockMvc.perform(post("/running-sessions/{id}/end", session.getRunningSessionId())
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "endedAt": "2026-08-10T08:00:00",
                          "durationSec": 1800,
                          "distanceKm": 4.8,
                          "intensity": "HIGH"
                        }
                        """));
    }

    @Test
    void 측정_이력이_없으면_rPPG가_기본이다() throws Exception {
        endRunning()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("HEART_RATE_CHECK"))
                .andExpect(jsonPath("$.data.defaultHeartRateSource").value("RPPG"));
    }

    @Test
    void 최근_측정이_워치면_워치가_기본이다() throws Exception {
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, 152, 168, 42, LocalDateTime.now().minusDays(1)));

        endRunning()
                .andExpect(jsonPath("$.data.defaultHeartRateSource").value("WATCH"));
    }

    /** 이미 끝난 세션에 다시 호출해도 에러 없이 같은 응답이어야 한다(멱등). */
    @Test
    void 두_번_종료해도_기본_측정_방식이_함께_온다() throws Exception {
        endRunning().andExpect(status().isOk());

        endRunning()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.defaultHeartRateSource").value("RPPG"));
    }
}
