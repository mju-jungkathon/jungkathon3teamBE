package jungkathon3team.aftergrow.auth.controller;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
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
class AuthLoginControllerTest {

    private static final String EMAIL = "runner@example.com";
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
        // @Transactional은 DB만 롤백하고 Redis는 되돌리지 않으므로 직접 지웁니다.
        redisTemplate.delete("refresh:" + userId);
    }

    private String body(String email, String password) {
        return """
                { "email": "%s", "password": "%s" }
                """.formatted(email, password);
    }

    @Test
    void 로그인에_성공하면_토큰_두_종류와_만료시간을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void 발급된_access_토큰에서_로그인한_userId를_꺼낼_수_있다() throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andReturn().getResponse().getContentAsString();

        String accessToken = response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        assertThat(jwtTokenProvider.parseAccessToken(accessToken)).isEqualTo(userId);
    }

    @Test
    void refresh_토큰이_Redis에_저장된다() throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andReturn().getResponse().getContentAsString();

        String refreshToken = response.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");
        assertThat(redisTemplate.opsForValue().get("refresh:" + userId)).isEqualTo(refreshToken);
        assertThat(redisTemplate.getExpire("refresh:" + userId)).isPositive();
    }

    @Test
    void 비밀번호가_틀리면_401과_E4011을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("E4011"));
    }

    /** 가입 여부가 응답으로 새면 안 되므로, 없는 이메일도 비밀번호 오류와 동일하게 답해야 합니다. */
    @Test
    void 없는_이메일은_비밀번호_오류와_똑같이_응답한다() throws Exception {
        String unknownEmail = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPassword = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, "wrong-password")))
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void 응답에_비밀번호_해시가_포함되지_않는다() throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(PASSWORD);
        assertThat(response).doesNotContain("passwordHash");
    }

    @Test
    void 이메일이_비어있으면_400과_E4001을_반환한다() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }
}
