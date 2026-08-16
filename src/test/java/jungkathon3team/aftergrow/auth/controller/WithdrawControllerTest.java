package jungkathon3team.aftergrow.auth.controller;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.RefreshTokenStore;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.running.entity.Intensity;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DELETE /users/me 회원 탈퇴 통합 테스트.
 *
 * <p><b>{@code @Transactional}을 쓰지 않는다.</b> 롤백되면 CASCADE가 실제로 동작했는지 확인할 수 없다.
 * 대신 각 테스트가 자기 사용자를 직접 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WithdrawControllerTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.signup(
                "withdraw-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(PASSWORD),
                "김러너",
                false));
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
    }

    /** 탈퇴에 실패한 테스트가 남긴 행을 지운다. Redis는 롤백되지 않으므로 함께 정리한다. */
    @AfterEach
    void tearDown() {
        userRepository.findById(user.getUserId()).ifPresent(userRepository::delete);
        refreshTokenStore.delete(user.getUserId());
    }

    private String body(String password) {
        return "{\"password\":\"" + password + "\"}";
    }

    @Test
    void 탈퇴에_성공하면_204와_함께_계정이_삭제된다() throws Exception {
        mockMvc.perform(delete("/users/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getUserId())).isEmpty();
    }

    /** users 행 하나를 지우면 DB CASCADE가 러닝 세션까지 정리하는지 실제로 확인한다. */
    @Test
    void 탈퇴하면_러닝_기록도_함께_삭제된다() throws Exception {
        RunningSession session = RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5);
        session.end(LocalDateTime.now(), 1800, 5.0, Intensity.MODERATE, null);
        UUID sessionId = runningSessionRepository.save(session).getRunningSessionId();
        assertThat(runningSessionRepository.findById(sessionId)).isPresent();

        mockMvc.perform(delete("/users/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(runningSessionRepository.findById(sessionId)).isEmpty();
    }

    @Test
    void 비밀번호가_틀리면_401_E4011이고_계정은_남는다() throws Exception {
        mockMvc.perform(delete("/users/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E4011"));

        assertThat(userRepository.findById(user.getUserId())).isPresent();
    }

    @Test
    void 비밀번호를_보내지_않으면_400_E4001() throws Exception {
        mockMvc.perform(delete("/users/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));

        assertThat(userRepository.findById(user.getUserId())).isPresent();
    }

    @Test
    void 탈퇴는_인증이_필요하다() throws Exception {
        mockMvc.perform(delete("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD)))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findById(user.getUserId())).isPresent();
    }

    /**
     * JWT는 취소할 수 없어 탈퇴 후에도 access 토큰의 서명은 유효하다.
     * 다만 사용자 행이 사라져 조회가 E4040으로 떨어지는지 확인한다.
     */
    @Test
    void 탈퇴_후에는_같은_토큰으로_프로필을_볼_수_없다() throws Exception {
        mockMvc.perform(delete("/users/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/me/profile").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("E4040"));
    }

    /** refresh 키가 남아 있으면 탈퇴한 계정으로 access 토큰이 계속 재발급된다. */
    @Test
    void 탈퇴하면_refresh_토큰도_지워져_재발급이_막힌다() throws Exception {
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());
        refreshTokenStore.save(user.getUserId(), refreshToken, jwtTokenProvider.getRefreshTokenTtl());
        assertThat(refreshTokenStore.matches(user.getUserId(), refreshToken)).isTrue();

        mockMvc.perform(delete("/users/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenStore.matches(user.getUserId(), refreshToken)).isFalse();
    }
}
