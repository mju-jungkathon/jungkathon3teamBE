package jungkathon3team.aftergrow.running;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jungkathon3team.aftergrow.running.entity.RoutePoint;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R3 러닝 세션(/running-sessions, /stretching-sessions) 통합 테스트.
 * <p>동시성(이미 진행 중 E4090)·소유권(E4030/E4040)·live 스냅샷·end 멱등, 그리고 R4 머지로 추가된
 * /end 응답의 defaultHeartRateSource 폴백을 러닝 관점에서 검증한다.
 * <p>기존 컨벤션대로 @SpringBootTest + MockMvc + @Transactional(자동 롤백). 실제 Postgres/Redis 필요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RunningSessionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /** JSONB 왕복을 보려면 영속성 컨텍스트 캐시를 비우고 DB에서 다시 읽어야 한다. */
    @Autowired
    private EntityManager entityManager;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = saveUser("run");
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
    }

    @Test
    void 러닝을_시작하면_201과_IN_PROGRESS를_반환한다() throws Exception {
        mockMvc.perform(post("/running-sessions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"2026-08-11T07:00:00\",\"location\":{\"lat\":37.5,\"lng\":127.0},\"uvIndexAtStart\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.runningSessionId").exists())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void 이미_진행중인_세션이_있으면_시작은_409_E4090() throws Exception {
        inProgress(user, LocalDateTime.now().minusMinutes(10));

        mockMvc.perform(post("/running-sessions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"2026-08-11T07:00:00\",\"location\":{\"lat\":37.5,\"lng\":127.0},\"uvIndexAtStart\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("E4090"));
    }

    @Test
    void 남의_세션_라이브_조회는_403_E4030() throws Exception {
        User other = saveUser("other");
        RunningSession othersSession = inProgress(other, LocalDateTime.now().minusMinutes(5));

        mockMvc.perform(get("/running-sessions/{id}/live", othersSession.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("E4030"));
    }

    @Test
    void 존재하지_않는_세션_라이브_조회는_404_E4040() throws Exception {
        mockMvc.perform(get("/running-sessions/{id}/live", UUID.randomUUID())
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("E4040"));
    }

    @Test
    void 라이브_조회에_distanceKm와_intensity를_보내면_스냅샷이_갱신된다() throws Exception {
        RunningSession session = inProgress(user, LocalDateTime.now().minusMinutes(15));

        mockMvc.perform(get("/running-sessions/{id}/live", session.getRunningSessionId())
                        .header("Authorization", bearer)
                        .param("distanceKm", "2.5")
                        .param("intensity", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distanceKm").value(2.5))
                .andExpect(jsonPath("$.data.intensity").value("HIGH"));
    }

    @Test
    void 러닝_종료는_ENDED로_바꾸고_다시_종료해도_멱등이다() throws Exception {
        RunningSession session = inProgress(user, LocalDateTime.now().minusMinutes(30));
        String body = "{\"endedAt\":\"2026-08-11T07:30:00\",\"durationSec\":1800,\"distanceKm\":5.0,\"intensity\":\"MODERATE\"}";

        mockMvc.perform(post("/running-sessions/{id}/end", session.getRunningSessionId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.nextStep").value("HEART_RATE_CHECK"));

        // 같은 세션에 end를 다시 호출해도 에러 없이 현재 상태(ENDED)를 반환
        mockMvc.perform(post("/running-sessions/{id}/end", session.getRunningSessionId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));
    }

    /** JSONB 컬럼 왕복(직렬화 → 저장 → 역직렬화)이 실제로 동작하는지 확인한다. */
    @Test
    void 러닝_종료시_보낸_GPS_경로가_저장된다() throws Exception {
        RunningSession session = inProgress(user, LocalDateTime.now().minusMinutes(30));

        mockMvc.perform(post("/running-sessions/{id}/end", session.getRunningSessionId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endedAt":"2026-08-11T07:30:00","durationSec":1800,"distanceKm":5.0,
                                 "intensity":"MODERATE",
                                 "routePath":[{"lat":37.5440,"lng":127.0557,"t":0},
                                              {"lat":37.5442,"lng":127.0559,"t":8}]}
                                """))
                .andExpect(status().isOk());

        runningSessionRepository.flush();
        entityManager.clear();

        List<RoutePoint> saved = runningSessionRepository.findById(session.getRunningSessionId())
                .orElseThrow().getRoutePath();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0)).isEqualTo(new RoutePoint(37.5440, 127.0557, 0));
        assertThat(saved.get(1).t()).isEqualTo(8);
    }

    @Test
    void 경로를_보내지_않고_종료해도_성공한다() throws Exception {
        RunningSession session = inProgress(user, LocalDateTime.now().minusMinutes(30));

        mockMvc.perform(post("/running-sessions/{id}/end", session.getRunningSessionId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endedAt\":\"2026-08-11T07:30:00\",\"durationSec\":1800,\"distanceKm\":5.0,\"intensity\":\"MODERATE\"}"))
                .andExpect(status().isOk());

        assertThat(runningSessionRepository.findById(session.getRunningSessionId())
                .orElseThrow().getRoutePath()).isNull();
    }

    @Test
    void 경로_점의_위경도_범위가_벗어나면_400과_E4001을_반환한다() throws Exception {
        RunningSession session = inProgress(user, LocalDateTime.now().minusMinutes(30));

        mockMvc.perform(post("/running-sessions/{id}/end", session.getRunningSessionId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endedAt":"2026-08-11T07:30:00","durationSec":1800,"distanceKm":5.0,
                                 "intensity":"MODERATE",
                                 "routePath":[{"lat":999.0,"lng":127.0557,"t":0}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void 종료_응답의_defaultHeartRateSource는_측정이력이_없으면_RPPG로_폴백한다() throws Exception {
        RunningSession session = inProgress(user, LocalDateTime.now().minusMinutes(30));

        mockMvc.perform(post("/running-sessions/{id}/end", session.getRunningSessionId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endedAt\":\"2026-08-11T07:30:00\",\"durationSec\":1800,\"distanceKm\":5.0,\"intensity\":\"MODERATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultHeartRateSource").value("RPPG"));
    }

    @Test
    void 러닝_준비는_위치_UV_스트레칭을_반환한다() throws Exception {
        mockMvc.perform(get("/running-sessions/prepare")
                        .header("Authorization", bearer)
                        .param("lat", "37.5")
                        .param("lng", "127.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationLabel").exists())
                .andExpect(jsonPath("$.data.uvIndex").exists())
                .andExpect(jsonPath("$.data.stretching.title").exists());
    }

    @Test
    void 스트레칭_시작은_201과_세션을_생성한다() throws Exception {
        mockMvc.perform(post("/stretching-sessions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PRE_RUN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stretchingSessionId").exists())
                .andExpect(jsonPath("$.data.startedAt").exists());
    }

    // --- helpers ---

    private User saveUser(String prefix) {
        return userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
    }

    private RunningSession inProgress(User owner, LocalDateTime startedAt) {
        return runningSessionRepository.save(RunningSession.start(owner, startedAt, 37.5, 127.0, 5));
    }
}
