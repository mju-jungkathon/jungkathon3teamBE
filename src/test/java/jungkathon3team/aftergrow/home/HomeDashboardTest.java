package jungkathon3team.aftergrow.home;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.profile.entity.UserGoal;
import jungkathon3team.aftergrow.profile.repository.UserGoalRepository;
import jungkathon3team.aftergrow.running.entity.Intensity;
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
 * R2 GET /home 대시보드 집계 로직 통합 테스트.
 * <p>주 기준 월~일, 주간 집계는 완료 세션(ENDED+COMPLETED)만, todayRunningStatus는 ENDED·COMPLETED→COMPLETED.
 * <p>기존 컨벤션대로 @SpringBootTest + MockMvc + @Transactional(자동 롤백). 실제 Postgres/Redis 필요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeDashboardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    @Autowired
    private UserGoalRepository userGoalRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("home-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
    }

    @Test
    void 주간_카운트는_이번주_완료세션만_세고_지난주와_진행중은_제외한다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        goal(5);
        completed(now.minusHours(2), 5.0, 5);   // 이번 주 완료 → 카운트
        completed(now.minusDays(9), 5.0, 5);    // 지난 주 완료 → 제외
        inProgress(now.minusHours(1));          // 이번 주 진행 중 → 제외

        mockMvc.perform(get("/home").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklyRunCount").value(1))
                .andExpect(jsonPath("$.data.weeklyGoalCount").value(5))
                .andExpect(jsonPath("$.data.remainingToGoal").value(4));
    }

    @Test
    void 오늘_완료세션이_있으면_todayRunningStatus는_COMPLETED다() throws Exception {
        completed(LocalDateTime.now().minusHours(1), 3.0, 5);

        mockMvc.perform(get("/home").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayRunningStatus").value("COMPLETED"));
    }

    @Test
    void 오늘_진행중_세션만_있으면_todayRunningStatus는_IN_PROGRESS다() throws Exception {
        inProgress(LocalDateTime.now().minusMinutes(20));

        mockMvc.perform(get("/home").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayRunningStatus").value("IN_PROGRESS"));
    }

    @Test
    void 주간_요약은_거리합_평균bpm_누적UV레벨을_계산한다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        RunningSession s1 = completed(now.minusHours(3), 5.0, 4);   // uv 4
        RunningSession s2 = completed(now.minusHours(2), 10.0, 6);  // uv 6 → 평균 5 → "보통"
        measurement(s1, 150, now.minusHours(3));
        measurement(s2, 148, now.minusHours(2));                   // 평균 149

        mockMvc.perform(get("/home").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklySummary.totalDistanceKm").value(15.0))
                .andExpect(jsonPath("$.data.weeklySummary.avgBpm").value(149))
                .andExpect(jsonPath("$.data.weeklySummary.cumulativeUvLevel").value("보통"));
    }

    @Test
    void 데이터가_없는_신규_유저는_기본값을_반환한다() throws Exception {
        mockMvc.perform(get("/home").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklyRunCount").value(0))
                .andExpect(jsonPath("$.data.weeklyGoalCount").value(0))
                .andExpect(jsonPath("$.data.remainingToGoal").value(0))
                .andExpect(jsonPath("$.data.todayRunningStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.latestMeasurement").doesNotExist())
                .andExpect(jsonPath("$.data.weeklySummary.totalDistanceKm").value(0.0))
                .andExpect(jsonPath("$.data.weeklySummary.avgBpm").doesNotExist())
                .andExpect(jsonPath("$.data.weeklySummary.cumulativeUvLevel").doesNotExist());
    }

    @Test
    void greeting은_닉네임이_없으면_러너로_대체한다() throws Exception {
        User noNick = userRepository.save(User.builder()
                .email("nonick-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .build());
        String token = "Bearer " + jwtTokenProvider.createAccessToken(noNick.getUserId());

        mockMvc.perform(get("/home").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greeting").value("안녕하세요, 러너님"));
    }

    // --- helpers ---

    private void goal(int weeklyRunGoal) {
        UserGoal g = UserGoal.create(user.getUserId());
        g.updatePartial(null, weeklyRunGoal);
        userGoalRepository.save(g);
    }

    private RunningSession completed(LocalDateTime startedAt, double distanceKm, int uvIndex) {
        RunningSession s = RunningSession.start(user, startedAt, 37.5, 127.0, uvIndex);
        s.end(startedAt.plusMinutes(30), 1800, distanceKm, Intensity.MODERATE, null);
        return runningSessionRepository.save(s);
    }

    private void inProgress(LocalDateTime startedAt) {
        runningSessionRepository.save(RunningSession.start(user, startedAt, 37.5, 127.0, 5));
    }

    private void measurement(RunningSession session, int avgBpm, LocalDateTime measuredAt) {
        heartRateMeasurementRepository.save(
                HeartRateMeasurement.watch(session, avgBpm, avgBpm + 10, 40, measuredAt));
    }
}
