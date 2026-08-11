package jungkathon3team.aftergrow.heartrate.controller;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** R4/R6 엔드포인트의 인가와 응답 래핑을 고정한다. 값 계산은 서비스 테스트가 덮는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HeartRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String bearer;
    private RunningSession session;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("ctrl-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    @Test
    void rPPG_안내는_ApiResponse로_감싸서_반환한다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements/rppg/guide").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.durationSec").value(12))
                .andExpect(jsonPath("$.data.instruction").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 측정_기록_목록은_range를_생략해도_조회된다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.sourceRatio.watch").value(0));
    }

    @Test
    void 잘못된_range는_400과_E4001이다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements").param("range", "abc")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void 측정_방식_선택은_다음_단계를_알려준다() throws Exception {
        mockMvc.perform(post("/running-sessions/{id}/heart-rate/select-source",
                        session.getRunningSessionId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heartRateSource\":\"RPPG\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("RPPG_GUIDE"));
    }

    @Test
    void rPPG_측정_시작은_201이다() throws Exception {
        mockMvc.perform(post("/heart-rate-measurements/rppg/start")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runningSessionId\":\"" + session.getRunningSessionId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MEASURING"));
    }

    @Test
    void 워치_데이터_업로드는_201이다() throws Exception {
        mockMvc.perform(post("/integrations/apple-health/heart-rate")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runningSessionId": "%s",
                                  "avgBpm": 152,
                                  "maxBpm": 168,
                                  "hrvMs": 42,
                                  "syncedAt": "2026-08-04T06:55:00"
                                }
                                """.formatted(session.getRunningSessionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.syncStatus").value("SUCCESS"));
    }

    @Test
    void 애플_헬스_연동_기록은_200이다() throws Exception {
        mockMvc.perform(post("/integrations/apple-health/link")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appleHealthLinked").value(true));
    }

    @Test
    void 요청_본문_검증에_실패하면_400과_E4001이다() throws Exception {
        mockMvc.perform(post("/heart-rate-measurements/rppg/start")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    // --- 인가 ---

    @Test
    void 토큰이_없으면_측정_기록을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E4010"));
    }

    @Test
    void 토큰이_없으면_rPPG_안내도_볼_수_없다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements/rppg/guide"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰이_없으면_애플_헬스_연동을_기록할_수_없다() throws Exception {
        mockMvc.perform(post("/integrations/apple-health/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linked\":true}"))
                .andExpect(status().isUnauthorized());
    }
}
