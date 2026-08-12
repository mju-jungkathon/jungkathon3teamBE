package jungkathon3team.aftergrow.common.config;

import jungkathon3team.aftergrow.auth.jwt.JwtAuthenticationFilter;
import jungkathon3team.aftergrow.common.exception.SecurityExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityExceptionHandler securityExceptionHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 로그인 전에도 호출해야 하는 경로만 엽니다.
     * <p>
     * {@code /auth/**} 와일드카드를 쓰지 않는 이유: {@code /auth/logout}은 누구를 로그아웃시킬지
     * 알아야 하므로 인증이 필요합니다. 와일드카드로 열어두면 토큰 없이 호출됐을 때
     * {@code @AuthenticationPrincipal}이 null로 들어옵니다. 앞으로 추가되는 {@code /auth/*}
     * 엔드포인트도 실수로 공개되지 않도록 명시적으로 나열합니다.
     */
    private static final String[] PUBLIC_PATHS = {
            "/auth/signup",
            "/auth/login",
            "/auth/refresh",
            // 로드밸런서·모니터링이 토큰 없이 호출한다. /actuator/** 와일드카드를 쓰지 않는
            // 이유는 위 /auth/** 와 같다 — 이후 노출되는 actuator 엔드포인트가 딸려 열린다.
            // actuator 기본 노출은 health 하나뿐이고 show-details 기본값이 never라
            // 응답은 {"status":"UP"}뿐이다. 별도 설정을 두지 않는다.
            "/actuator/health",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // JWT 기반 stateless API라 세션/CSRF 토큰을 쓰지 않음
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 기본 EntryPoint는 인증 없음에도 403을 주므로 401/403을 구분해 직접 처리
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                // 인가 판단 전에 토큰을 읽어 SecurityContext를 채워야 하므로 앞쪽에 배치
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
