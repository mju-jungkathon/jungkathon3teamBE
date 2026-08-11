package jungkathon3team.aftergrow.profile;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R7 프로필 & 설정(/users/me/*) 통합 테스트.
 * <p>정책 고정: 설정 행이 없는 신규 유저는 GET에서 기본값을 받고, PATCH는 해당 테이블 행만 upsert한다.
 * PATCH는 부분 수정(생략 필드는 기존값 유지), weeklyRunGoal은 0 이상(@Min(0))만 허용한다.
 * <p>기존 컨벤션대로 @SpringBootTest + MockMvc + @Transactional(자동 롤백). 실제 Postgres/Redis 필요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String bearer;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("profile-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
    }

    @Test
    void 설정이_없는_신규_유저의_프로필은_기본값을_반환한다() throws Exception {
        mockMvc.perform(get("/users/me/profile").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("김러너"))
                .andExpect(jsonPath("$.data.goal.goalType").doesNotExist())
                .andExpect(jsonPath("$.data.goal.weeklyRunGoal").doesNotExist())
                .andExpect(jsonPath("$.data.integrations.locationLinked").value(false))
                .andExpect(jsonPath("$.data.integrations.appleHealthLinked").value(false))
                .andExpect(jsonPath("$.data.notifications.weeklyReportDay").doesNotExist());
    }

    @Test
    void 목표_최초_수정은_행을_생성하고_updatedAt을_채운다() throws Exception {
        mockMvc.perform(patch("/users/me/goal")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("{\"goalType\":\"체력 증진\",\"weeklyRunGoal\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalType").value("체력 증진"))
                .andExpect(jsonPath("$.data.weeklyRunGoal").value(5))
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void 목표_수정은_부분_수정이라_생략한_필드는_기존값을_유지한다() throws Exception {
        // 먼저 두 필드를 저장
        mockMvc.perform(patch("/users/me/goal")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("{\"goalType\":\"체력 증진\",\"weeklyRunGoal\":5}"))
                .andExpect(status().isOk());

        // goalType만 다시 보내면 weeklyRunGoal(5)은 그대로여야 한다
        mockMvc.perform(patch("/users/me/goal")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("{\"goalType\":\"근력 강화\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalType").value("근력 강화"))
                .andExpect(jsonPath("$.data.weeklyRunGoal").value(5));
    }

    @Test
    void 목표_횟수가_음수면_400_E4001() throws Exception {
        mockMvc.perform(patch("/users/me/goal")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weeklyRunGoal\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void 연동상태는_행이_없으면_전부_false를_반환한다() throws Exception {
        mockMvc.perform(get("/users/me/integrations").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationLinked").value(false))
                .andExpect(jsonPath("$.data.cameraPermission").value(false))
                .andExpect(jsonPath("$.data.locationPermission").value(false))
                .andExpect(jsonPath("$.data.appleHealthLinked").value(false));
    }

    @Test
    void 알림설정_수정은_저장되고_요일은_영문으로_왕복한다() throws Exception {
        mockMvc.perform(patch("/users/me/notifications")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runningReminderTime\":\"07:00\",\"weeklyReportDay\":\"SUNDAY\",\"weeklyReportTime\":\"20:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklyReportDay").value("SUNDAY"))
                .andExpect(jsonPath("$.data.runningReminderTime").exists());

        // 저장된 값이 프로필 조회에도 반영되는지 확인
        mockMvc.perform(get("/users/me/profile").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.weeklyReportDay").value("SUNDAY"));
    }
}
