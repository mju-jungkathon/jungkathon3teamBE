package jungkathon3team.aftergrow.recovery;

import com.jayway.jsonpath.JsonPath;
import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.running.entity.Intensity;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R5 회복 가이드(5.1 생성 / 5.2 쿨다운 타이머 / 5.3 완료) 통합 테스트.
 * <p>기존 컨벤션대로 @SpringBootTest + MockMvc + @Transactional(자동 롤백). 실제 Postgres/Redis 필요.
 * <p>{@code openai.api-key}를 빈 값으로 고정해 항상 규칙 기반({@code MockRecoveryGuideAiClient})으로
 * 떨어지게 한다 — 로컬에 OPENAI_API_KEY가 있어도 테스트가 네트워크를 타면 안 되기 때문.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "openai.api-key=")
class RecoveryGuideApiTest {

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

    @BeforeEach
    void setUp() {
        user = saveUser("recovery");
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
    }

    @Test
    void 회복_가이드를_생성하면_201과_요약_액션_쿨다운을_반환한다() throws Exception {
        RunningSession session = endedSession(user, 6.2, Intensity.HIGH, 7);

        mockMvc.perform(post("/running-sessions/{id}/recovery-guide", session.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recoveryGuideId").exists())
                .andExpect(jsonPath("$.data.summaryMessage").isNotEmpty())
                .andExpect(jsonPath("$.data.actions").isNotEmpty())
                .andExpect(jsonPath("$.data.actions[0].type").isNotEmpty())
                .andExpect(jsonPath("$.data.actions[0].title").isNotEmpty())
                .andExpect(jsonPath("$.data.cooldownTimerSec").isNumber());
    }

    @Test
    void 측정된_심박수가_있으면_가이드에_measuredBpm으로_담긴다() throws Exception {
        RunningSession session = endedSession(user, 3.0, Intensity.MODERATE, 3);
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, 152, 171, 42, LocalDateTime.now()));

        mockMvc.perform(post("/running-sessions/{id}/recovery-guide", session.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.measuredBpm").value(152));
    }

    @Test
    void 측정_기록이_없으면_measuredBpm은_null이다() throws Exception {
        RunningSession session = endedSession(user, 3.0, Intensity.LOW, 2);

        mockMvc.perform(post("/running-sessions/{id}/recovery-guide", session.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.measuredBpm").isEmpty());
    }

    @Test
    void UV_노출량이_높은_세션에는_UV_CARE_액션이_추가된다() throws Exception {
        // endedSession의 durationSec은 1800초(30분) 고정 — UV 10 × 0.5h = 5.0(dose)로 '높음' 등급을 확실히 넘긴다.
        RunningSession session = endedSession(user, 6.0, Intensity.HIGH, 10);

        mockMvc.perform(post("/running-sessions/{id}/recovery-guide", session.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.actions[?(@.type == 'UV_CARE')]").isNotEmpty());
    }

    @Test
    void 가이드_생성을_두_번_호출해도_재생성하지_않는다() throws Exception {
        RunningSession session = endedSession(user, 5.0, Intensity.MODERATE, 4);

        String first = generateGuideId(session);

        // 같은 세션이면 항상 같은 가이드 (running_session_id UNIQUE, 멱등)
        mockMvc.perform(post("/running-sessions/{id}/recovery-guide", session.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recoveryGuideId").value(first));
    }

    @Test
    void 남의_세션에_가이드를_생성하면_403_E4030() throws Exception {
        RunningSession othersSession = endedSession(saveUser("other"), 5.0, Intensity.MODERATE, 4);

        mockMvc.perform(post("/running-sessions/{id}/recovery-guide", othersSession.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("E4030"));
    }

    @Test
    void 존재하지_않는_세션에_가이드를_생성하면_404_E4040() throws Exception {
        mockMvc.perform(post("/running-sessions/{id}/recovery-guide", UUID.randomUUID())
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("E4040"));
    }

    @Test
    void 쿨다운_타이머_시작은_저장된_길이와_시작시각을_반환한다() throws Exception {
        RunningSession session = endedSession(user, 6.2, Intensity.HIGH, 5);
        String guideId = generateGuideId(session);

        mockMvc.perform(post("/recovery-guides/{id}/cooldown-timer/start", guideId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cooldownTimerSec").isNumber())
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty());
    }

    @Test
    void 남의_가이드로_쿨다운_타이머를_시작하면_403_E4030() throws Exception {
        RunningSession othersSession = endedSession(saveUser("other"), 5.0, Intensity.MODERATE, 4);
        String othersGuideId = generateGuideId(othersSession, bearerOf(othersSession));

        mockMvc.perform(post("/recovery-guides/{id}/cooldown-timer/start", othersGuideId)
                        .header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("E4030"));
    }

    @Test
    void 완료하면_COMPLETED가_되고_reportId는_가이드id다() throws Exception {
        RunningSession session = endedSession(user, 5.0, Intensity.MODERATE, 4);
        String guideId = generateGuideId(session);

        mockMvc.perform(post("/running-sessions/{id}/complete", session.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.reportId").value(guideId));
    }

    @Test
    void 가이드_없이_완료하면_404_E4040() throws Exception {
        RunningSession session = endedSession(user, 5.0, Intensity.MODERATE, 4);

        mockMvc.perform(post("/running-sessions/{id}/complete", session.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("E4040"));
    }

    // --- helpers ---

    private String generateGuideId(RunningSession session) throws Exception {
        return generateGuideId(session, bearer);
    }

    private String generateGuideId(RunningSession session, String authorization) throws Exception {
        MvcResult result = mockMvc.perform(post("/running-sessions/{id}/recovery-guide", session.getRunningSessionId())
                        .header("Authorization", authorization))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.recoveryGuideId");
    }

    private String bearerOf(RunningSession session) {
        return "Bearer " + jwtTokenProvider.createAccessToken(session.getUser().getUserId());
    }

    private User saveUser(String prefix) {
        return userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
    }

    private RunningSession endedSession(User owner, double distanceKm, Intensity intensity, int uvIndexAtStart) {
        RunningSession session = RunningSession.start(
                owner, LocalDateTime.now().minusMinutes(40), 37.5, 127.0, uvIndexAtStart);
        session.end(LocalDateTime.now(), 1800, distanceKm, intensity, null);
        return runningSessionRepository.save(session);
    }
}
