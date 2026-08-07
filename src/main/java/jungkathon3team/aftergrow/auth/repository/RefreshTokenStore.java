package jungkathon3team.aftergrow.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.UUID;

/**
 * refresh 토큰을 Redis에 보관합니다. 로그아웃 시 즉시 무효화하기 위한 저장소이며,
 * 사라져도 재로그인으로 복구되는 데이터라 PostgreSQL이 아닌 Redis에 둡니다.
 * <p>
 * 조회/삭제는 아직 읽는 쪽(/auth/refresh, /auth/logout)이 없어 추가하지 않았습니다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    /** 사용자당 하나만 유지합니다. 다시 로그인하면 이전 refresh 토큰은 덮어써져 무효가 됩니다. */
    public void save(UUID userId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId), refreshToken, ttl);
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
