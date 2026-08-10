package jungkathon3team.aftergrow.heartrate.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis는 @Transactional로 롤백되지 않으므로 테스트에서 직접 키를 지운다.
 */
@SpringBootTest
class RppgSessionStoreTest {

    @Autowired
    private RppgSessionStore rppgSessionStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final UUID rppgSessionId = UUID.randomUUID();
    private final UUID runningSessionId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        redisTemplate.delete(RppgSessionStore.KEY_PREFIX + rppgSessionId);
    }

    @Test
    void 저장한_러닝_세션_id를_다시_꺼낼_수_있다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        assertThat(rppgSessionStore.findRunningSessionId(rppgSessionId))
                .contains(runningSessionId);
    }

    @Test
    void 저장하지_않은_id를_조회하면_비어_있다() {
        assertThat(rppgSessionStore.findRunningSessionId(UUID.randomUUID()))
                .isEmpty();
    }

    /** 같은 rppgSessionId로 결과를 두 번 제출할 수 없어야 한다. */
    @Test
    void 삭제하면_더_이상_조회되지_않는다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        rppgSessionStore.delete(rppgSessionId);

        assertThat(rppgSessionStore.findRunningSessionId(rppgSessionId)).isEmpty();
    }

    @Test
    void 없는_키를_삭제해도_조용히_넘어간다() {
        rppgSessionStore.delete(UUID.randomUUID());
    }

    /** 측정을 끝내지 않고 앱을 꺼도 키가 영원히 남지 않아야 한다. */
    @Test
    void 저장된_키에는_만료_시간이_걸려_있다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        Long ttlSeconds = redisTemplate.getExpire(RppgSessionStore.KEY_PREFIX + rppgSessionId);

        assertThat(ttlSeconds).isNotNull().isPositive().isLessThanOrEqualTo(600);
    }
}
