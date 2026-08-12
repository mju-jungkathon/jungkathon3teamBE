package jungkathon3team.aftergrow.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void API_문서는_인증_없이_열린다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void 보호된_경로는_인증_없이_접근하면_401과_E4010을_반환한다() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("E4010"));
    }

    /**
     * 헬스체크는 로드밸런서·모니터링이 토큰 없이 호출한다.
     * <p>이 테스트 하나가 두 가지를 동시에 지킨다 — actuator 의존성이 없으면 404,
     * PUBLIC_PATHS에 없으면 401이라 어느 쪽이 빠져도 실패한다.
     * <p>검증이 {@code $.data.…}가 아니라 {@code $.status}인 이유: actuator 응답은
     * ApiResponse 래퍼를 거치지 않는다. 이 레포에서 유일한 예외다.
     */
    @Test
    void 헬스체크는_인증_없이_열린다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
