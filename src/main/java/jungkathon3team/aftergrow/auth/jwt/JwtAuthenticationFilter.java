package jungkathon3team.aftergrow.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authorization 헤더의 JWT를 검증해 "이 요청은 누구의 것인지"를 SecurityContext에 기록합니다.
 * <p>
 * <b>이 필터는 요청을 거절하지 않습니다.</b> 토큰이 없거나 유효하지 않으면 아무것도 기록하지 않고
 * 그대로 넘기며, 실제 거절은 {@code SecurityConfig}의 인가 규칙과
 * {@code SecurityExceptionHandler}(401/E4010)가 담당합니다.
 * 그래야 permitAll 경로가 잘못된 토큰 때문에 막히지 않습니다.
 * <p>
 * 서명이 유효하다는 것은 서버가 발급한 토큰이고 userId가 위조되지 않았다는 뜻이므로,
 * 사용자 존재 여부를 매 요청마다 DB에서 다시 확인하지 않습니다.
 * 탈퇴·차단 즉시 반영이나 권한 구분이 필요해지면 그때 조회를 추가하세요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        resolveToken(request).ifPresent(token -> authenticate(token, request));
        filterChain.doFilter(request, response);
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            UUID userId = jwtTokenProvider.parseAccessToken(token);

            var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            // 위조·만료·타입 불일치 모두 여기로 옵니다. 인증 없이 넘겨 SecurityConfig가 401을 내게 합니다.
            log.debug("토큰 검증 실패: {}", e.getMessage());
        }
    }
}
