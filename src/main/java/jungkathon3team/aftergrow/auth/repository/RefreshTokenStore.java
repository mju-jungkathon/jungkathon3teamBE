package jungkathon3team.aftergrow.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;

/**
 * refresh 토큰을 Redis에 보관합니다. 로그아웃 시 즉시 무효화하기 위한 저장소이며,
 * 사라져도 재로그인으로 복구되는 데이터라 PostgreSQL이 아닌 Redis에 둡니다.
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

    /**
     * 저장된 refresh 토큰이 넘어온 것과 같은지 확인합니다.
     * <p>
     * JWT는 서명만으로는 취소할 수 없어, 서버가 발급 이력을 끊을 수 있는 유일한 지점입니다.
     * 로그아웃으로 삭제됐거나 재로그인으로 덮어써졌다면 서명이 유효해도 false입니다.
     */
    public boolean matches(UUID userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(key(userId));
        // 저장값이 없으면(로그아웃/만료) 무조건 거부. 타이밍 공격 여지를 줄이려 상수 시간 비교를 씁니다.
        return stored != null && MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                refreshToken.getBytes(StandardCharsets.UTF_8));
    }

    /** 로그아웃. 이미 없어도 조용히 넘어갑니다(같은 요청이 두 번 와도 결과가 같아야 함). */
    public void delete(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
