package jungkathon3team.aftergrow.auth.jwt;

import jungkathon3team.aftergrow.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 아직 보호된 도메인 API가 없어 테스트 전용 엔드포인트를 띄워 검증합니다.
 * 도메인 API가 실제로 사용할 {@code @AuthenticationPrincipal} 주입까지 함께 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtAuthenticationFilterTest.ProbeController.class)
class JwtAuthenticationFilterTest {

    private static final String PROTECTED_PATH = "/test/me";

    /**
     * 테스트 클래스의 중첩 클래스는 자동 스캔되지 않으므로 {@code @Import}로 한 번만 등록합니다.
     * ({@code @TestConfiguration} 안에 두면 설정 파싱과 {@code @Bean} 양쪽으로 등록되어 매핑이 충돌합니다.)
     */
    @RestController
    static class ProbeController {
        @GetMapping(PROTECTED_PATH)
        public ApiResponse<String> me(@AuthenticationPrincipal UUID userId) {
            return ApiResponse.ok(userId == null ? "anonymous" : userId.toString());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 유효한_access_토큰이면_통과하고_userId가_주입된다() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.createAccessToken(userId);

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(userId.toString()));
    }

    @Test
    void 토큰이_없으면_401과_E4010을_반환한다() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E4010"));
    }

    @Test
    void 위조된_토큰은_401이다() throws Exception {
        JwtTokenProvider attacker =
                new JwtTokenProvider("a-totally-different-secret-key-long-enough-to-sign", 3_600_000L, 1L);

        mockMvc.perform(get(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + attacker.createAccessToken(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E4010"));
    }

    /** refresh 토큰으로는 API에 접근할 수 없어야 합니다. 수명이 30일이라 access 대용으로 쓰이면 위험합니다. */
    @Test
    void refresh_토큰으로는_접근할_수_없다() throws Exception {
        String refreshToken = jwtTokenProvider.createRefreshToken(UUID.randomUUID());

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E4010"));
    }

    @Test
    void 만료된_토큰은_401이다() throws Exception {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                "test-only-secret-key-not-used-anywhere-else-1234567890", 1L, 1L);
        String expired = shortLived.createAccessToken(UUID.randomUUID());
        Thread.sleep(50);

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E4010"));
    }

    @Test
    void Bearer_접두사가_없으면_401이다() throws Exception {
        String token = jwtTokenProvider.createAccessToken(UUID.randomUUID());

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    /** 잘못된 토큰이 딸려와도 공개 경로는 막히면 안 됩니다. 필터가 거절하지 않고 넘기는지 확인합니다. */
    @Test
    void 공개_경로는_잘못된_토큰이_있어도_열려_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer garbage"))
                .andExpect(status().isOk());
    }

    /**
     * 인증을 통과한 뒤 없는 경로로 가면 404여야 합니다.
     * catch-all 핸들러가 NoResourceFoundException을 삼켜 500을 내던 문제의 회귀 테스트입니다.
     */
    @Test
    void 인증_통과_후_없는_경로는_404와_E4040을_반환한다() throws Exception {
        String token = jwtTokenProvider.createAccessToken(UUID.randomUUID());

        mockMvc.perform(get("/존재하지-않는-경로").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("E4040"));
    }

    @Test
    void 지원하지_않는_HTTP_메서드는_404와_E4040을_반환한다() throws Exception {
        String token = jwtTokenProvider.createAccessToken(UUID.randomUUID());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("E4040"));
    }
}
