package jungkathon3team.aftergrow.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    /** access 토큰을 refresh 자리에(또는 그 반대로) 쓰지 못하도록 구분하는 클레임 */
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        // HS256은 256비트 이상을 요구합니다. 짧은 secret이면 여기서 바로 기동 실패합니다.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMillis(accessTokenExpirationMs);
        this.refreshTokenTtl = Duration.ofMillis(refreshTokenExpirationMs);
    }

    public String createAccessToken(UUID userId) {
        return create(userId, TYPE_ACCESS, accessTokenTtl);
    }

    public String createRefreshToken(UUID userId) {
        return create(userId, TYPE_REFRESH, refreshTokenTtl);
    }

    /** access 토큰에서 userId를 꺼냅니다. 서명·만료·타입이 어긋나면 E4010입니다. */
    public UUID parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public UUID parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    private String create(UUID userId, String type, Duration ttl) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    private UUID parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 위조, 만료, 형식 오류를 모두 같은 응답으로 처리합니다.
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
