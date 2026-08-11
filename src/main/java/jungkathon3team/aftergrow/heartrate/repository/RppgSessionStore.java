package jungkathon3team.aftergrow.heartrate.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * rPPG 측정 중(R4.5 → R4.6)에만 필요한 {@code rppgSessionId → runningSessionId} 매핑을 Redis에 보관합니다.
 * <p>
 * R4.6 요청 본문에 runningSessionId가 없어 서버가 이 매핑을 들고 있어야 합니다.
 * 측정을 끝내지 않고 앱을 꺼도 TTL로 사라지므로, Postgres에 미완성 측정 행이 남지 않습니다
 * (남으면 R6.1 목록과 sourceRatio 집계에서 매번 걸러내야 합니다).
 */
@Repository
@RequiredArgsConstructor
public class RppgSessionStore {

    public static final String KEY_PREFIX = "rppg:";

    /** 측정 자체는 12초지만, 네트워크 지연·앱 전환을 감안해 넉넉히 잡습니다. */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public void save(UUID rppgSessionId, UUID runningSessionId) {
        redisTemplate.opsForValue().set(key(rppgSessionId), runningSessionId.toString(), TTL);
    }

    /**
     * GETDEL로 조회와 삭제를 한 번의 Redis 왕복에 묶은 원자적 claim입니다.
     * 더블 탭이나 타임아웃 재시도로 같은 rppgSessionId 제출이 동시에 들어와도 값을 가져가는 쪽은
     * 하나뿐이라 측정 기록이 두 번 쌓이는 일이 원천적으로 불가능합니다. 만료됐거나 애초에 없던 id,
     * 혹은 이미 다른 요청이 가져간 id면 비어 있습니다. 호출자는 E4040으로 응답합니다.
     */
    public Optional<UUID> claimRunningSessionId(UUID rppgSessionId) {
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key(rppgSessionId)))
                .map(UUID::fromString);
    }

    private String key(UUID rppgSessionId) {
        return KEY_PREFIX + rppgSessionId;
    }
}
