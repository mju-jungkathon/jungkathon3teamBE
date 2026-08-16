package jungkathon3team.aftergrow.running;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jungkathon3team.aftergrow.running.entity.Intensity;
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

    // --- 기록 조회 ---

    @Test
    void 러닝_기록_목록은_최신순으로_반환하고_집계를_함께_준다() throws Exception {
        ended(user, LocalDateTime.now().minusDays(3), 5.0, 1800);
        ended(user, LocalDateTime.now().minusDays(1), 3.2, 1200);

        mockMvc.perform(get("/running-sessions").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2))
                // 최신(1일 전, 3.2km)이 먼저
                .andExpect(jsonPath("$.data.records[0].distanceKm").value(3.2))
                .andExpect(jsonPath("$.data.records[1].distanceKm").value(5.0))
                .andExpect(jsonPath("$.data.summary.totalCount").value(2))
                .andExpect(jsonPath("$.data.summary.totalDistanceKm").value(8.2))
                .andExpect(jsonPath("$.data.summary.totalDurationSec").value(3000));
    }

    /** 목록에 경로를 실으면 세션당 수백 점이라 응답이 수백 KB가 된다. 있다/없다만 알려준다. */
    @Test
    void 목록에는_경로가_실리지_않고_보유_여부만_내려간다() throws Exception {
        RunningSession session = inProgress(user, LocalDateTime.now().minusMinutes(30));
        session.end(LocalDateTime.now(), 600, 2.0, Intensity.LOW,
                List.of(new RoutePoint(37.5, 127.0, 0), new RoutePoint(37.51, 127.01, 10)));
        runningSessionRepository.save(session);

        mockMvc.perform(get("/running-sessions").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].hasRoutePath").value(true))
                .andExpect(jsonPath("$.data.records[0].routePath").doesNotExist());
    }

    @Test
    void 기록_목록은_range_밖의_세션을_제외한다() throws Exception {
        ended(user, LocalDateTime.now().minusDays(40), 5.0, 1800);
        ended(user, LocalDateTime.now().minusDays(2), 3.0, 1200);

        mockMvc.perform(get("/running-sessions").header("Authorization", bearer)
                        .param("range", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].distanceKm").value(3.0));
    }

    @Test
    void 잘못된_range는_400과_E4001을_반환한다() throws Exception {
        mockMvc.perform(get("/running-sessions").header("Authorization", bearer)
                        .param("range", "4w"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void 기록_목록은_남의_세션을_포함하지_않는다() throws Exception {
        ended(saveUser("other"), LocalDateTime.now().minusDays(1), 9.9, 3000);

        mockMvc.perform(get("/running-sessions").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(0))
                .andExpect(jsonPath("$.data.summary.totalCount").value(0));
    }

    @Test
    void 기록_상세는_지도에_그릴_경로를_포함한다() throws Exception {
        RunningSession session = inProgress(user, LocalDateTime.now().minusMinutes(30));
        session.end(LocalDateTime.now(), 600, 2.0, Intensity.LOW,
                List.of(new RoutePoint(37.5440, 127.0557, 0), new RoutePoint(37.5442, 127.0559, 8)));
        runningSessionRepository.save(session);

        mockMvc.perform(get("/running-sessions/{id}", session.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routePath.length()").value(2))
                .andExpect(jsonPath("$.data.routePath[0].lat").value(37.5440))
                .andExpect(jsonPath("$.data.routePath[0].t").value(0))
                .andExpect(jsonPath("$.data.startLocation.lat").value(37.5))
                .andExpect(jsonPath("$.data.status").value("ENDED"));
    }

    @Test
    void 경로_없이_끝난_세션의_상세는_routePath가_null이다() throws Exception {
        RunningSession session = ended(user, LocalDateTime.now().minusDays(1), 3.0, 1200);

        mockMvc.perform(get("/running-sessions/{id}", session.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routePath").doesNotExist());
    }

    @Test
    void 남의_세션_상세는_403_E4030() throws Exception {
        RunningSession other = ended(saveUser("other"), LocalDateTime.now().minusDays(1), 3.0, 1200);

        mockMvc.perform(get("/running-sessions/{id}", other.getRunningSessionId())
                        .header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("E4030"));
    }

    @Test
    void 없는_세션_상세는_404_E4040() throws Exception {
        mockMvc.perform(get("/running-sessions/{id}", UUID.randomUUID())
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("E4040"));
    }

    /** /{id}가 /prepare를 삼키면 러닝 준비 화면이 통째로 깨진다. 회귀 방지용. */
    @Test
    void 상세_조회_경로가_prepare를_가리지_않는다() throws Exception {
        mockMvc.perform(get("/running-sessions/prepare")
                        .header("Authorization", bearer)
                        .param("lat", "37.5").param("lng", "127.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uvIndex").exists());
    }

    @Test
    void 기록_조회는_인증이_필요하다() throws Exception {
        mockMvc.perform(get("/running-sessions"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private RunningSession ended(User owner, LocalDateTime startedAt, double distanceKm, int durationSec) {
        RunningSession session = RunningSession.start(owner, startedAt, 37.5, 127.0, 5);
        session.end(startedAt.plusSeconds(durationSec), durationSec, distanceKm, Intensity.MODERATE, null);
        return runningSessionRepository.save(session);
    }

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
