package jungkathon3team.aftergrow.auth.controller;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.RefreshTokenStore;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthRefreshLogoutControllerTest {

    private static final String EMAIL = "refresher@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userId = userRepository.save(User.builder()
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .nickname("김러너")
                .build()).getUserId();
        // Redis는 @Transactional로 롤백되지 않으므로 직접 지웁니다.
        redisTemplate.delete("refresh:" + userId);
    }

    /** 로그인해서 실제 발급 경로를 그대로 탄 refresh 토큰을 얻습니다. */
    private String loginAndGetRefreshToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");
    }

    private void refresh(String refreshToken, int expectedStatus, String expectedErrorCode) throws Exception {
        var result = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().is(expectedStatus));
        if (expectedErrorCode != null) {
            result.andExpect(jsonPath("$.error.code").value(expectedErrorCode));
        }
    }

    // --- refresh ---

    @Test
    void 유효한_refresh_토큰이면_새_access_토큰을_발급한다() throws Exception {
        String refreshToken = loginAndGetRefreshToken();

        String body = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();

        String newAccessToken = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        assertThat(jwtTokenProvider.parseAccessToken(newAccessToken)).isEqualTo(userId);
    }

    @Test
    void 재발급_응답에는_refresh_토큰이_포함되지_않는다() throws Exception {
        String refreshToken = loginAndGetRefreshToken();

        String body = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("refreshToken");
    }

    /** access 토큰은 수명이 짧아 재발급 자격이 없어야 합니다. */
    @Test
    void access_토큰으로_재발급을_요청하면_401이다() throws Exception {
        refresh(jwtTokenProvider.createAccessToken(userId), 401, "E4010");
    }

    @Test
    void 위조된_refresh_토큰은_401이다() throws Exception {
        JwtTokenProvider attacker =
                new JwtTokenProvider("a-totally-different-secret-key-long-enough-to-sign", 1L, 3_600_000L);
        refresh(attacker.createRefreshToken(userId), 401, "E4010");
    }

    /**
     * 서명이 유효해도 Redis에 없으면 거부해야 합니다.
     * 이 검사가 없으면 로그아웃이 무의미해집니다.
     */
    @Test
    void 서명은_유효하지만_저장되지_않은_refresh_토큰은_401이다() throws Exception {
        String neverStored = jwtTokenProvider.createRefreshToken(userId);
        refresh(neverStored, 401, "E4010");
    }

    @Test
    void 재로그인하면_이전_refresh_토큰은_무효가_된다() throws Exception {
        String oldToken = loginAndGetRefreshToken();
        Thread.sleep(1000);   // iat가 초 단위라 토큰이 달라지도록 간격을 둠
        String newToken = loginAndGetRefreshToken();

        assertThat(oldToken).isNotEqualTo(newToken);
        refresh(oldToken, 401, "E4010");
        refresh(newToken, 200, null);
    }

    // --- logout ---

    @Test
    void 로그아웃하면_204와_빈_본문을_반환한다() throws Exception {
        loginAndGetRefreshToken();
        String accessToken = jwtTokenProvider.createAccessToken(userId);

        String body = mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEmpty();
    }

    @Test
    void 로그아웃하면_저장된_refresh_토큰이_삭제된다() throws Exception {
        loginAndGetRefreshToken();
        assertThat(redisTemplate.opsForValue().get("refresh:" + userId)).isNotNull();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(userId)))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.opsForValue().get("refresh:" + userId)).isNull();
    }

    /** 로그아웃의 실제 효과는 "더 이상 재발급되지 않는 것"입니다. */
    @Test
    void 로그아웃한_뒤에는_재발급이_거부된다() throws Exception {
        String refreshToken = loginAndGetRefreshToken();
        refresh(refreshToken, 200, null);

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(userId)))
                .andExpect(status().isNoContent());

        refresh(refreshToken, 401, "E4010");
    }

    @Test
    void 로그아웃은_인증이_필요하다() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E4010"));
    }

    /** 같은 요청이 두 번 와도 결과가 같아야 합니다. */
    @Test
    void 로그아웃을_두_번_해도_204다() throws Exception {
        loginAndGetRefreshToken();
        String accessToken = jwtTokenProvider.createAccessToken(userId);

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void 다른_사용자의_refresh_토큰으로는_재발급되지_않는다() throws Exception {
        loginAndGetRefreshToken();
        UUID otherUserId = UUID.randomUUID();

        refresh(jwtTokenProvider.createRefreshToken(otherUserId), 401, "E4010");
        // 피해자의 토큰은 그대로 살아 있어야 합니다.
        assertThat(refreshTokenStore.matches(userId,
                redisTemplate.opsForValue().get("refresh:" + userId))).isTrue();
    }
}
