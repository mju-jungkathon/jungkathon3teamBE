package jungkathon3team.aftergrow.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI에 Authorize 버튼을 띄우기 위한 문서 설정입니다.
 * <p>
 * <b>서버의 인증 동작은 바꾸지 않습니다.</b> "이 API들은 Authorization: Bearer 헤더를 쓴다"고
 * OpenAPI 문서에 알려줄 뿐이며, 실제 검증은 {@code JwtAuthenticationFilter}가 합니다.
 * <p>
 * 전역으로 걸어두었으므로, 토큰 없이 호출하는 엔드포인트에는
 * {@code @SecurityRequirements}(복수형, 빈 값)를 붙여 해제하세요.
 */
@Configuration
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "로그인 응답의 accessToken을 넣으세요. refreshToken이 아닙니다."
)
@OpenAPIDefinition(
        info = @Info(
                title = "AfterGrow API",
                version = "v0.0.1",
                description = """
                        러닝 트래킹 + 심박수 측정 + AI 회복 가이드 백엔드.

                        모든 응답은 { success, data, error } 형태로 감싸집니다.
                        인증이 필요한 API는 우측 상단 Authorize에 accessToken을 넣고 호출하세요.
                        """
        ),
        servers = @Server(url = "/", description = "현재 서버"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
