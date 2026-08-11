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
    void 저장한_러닝_세션_id를_claim으로_꺼낼_수_있다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        assertThat(rppgSessionStore.claimRunningSessionId(rppgSessionId))
                .contains(runningSessionId);
    }

    @Test
    void 저장하지_않은_id를_claim하면_비어_있다() {
        assertThat(rppgSessionStore.claimRunningSessionId(UUID.randomUUID()))
                .isEmpty();
    }

    /** claim은 GETDEL이라 조회와 동시에 키가 지워진다 — 같은 rppgSessionId로 두 번 제출할 수 없어야 한다. */
    @Test
    void claim하면_키가_사라져_다시_조회되지_않는다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        rppgSessionStore.claimRunningSessionId(rppgSessionId);

        assertThat(rppgSessionStore.claimRunningSessionId(rppgSessionId)).isEmpty();
    }

    /** 이중 제출 시나리오: 두 번째 claim은 반드시 빈 값이어야 중복 저장이 생기지 않는다. */
    @Test
    void 두_번째_claim은_항상_비어_있다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        var first = rppgSessionStore.claimRunningSessionId(rppgSessionId);
        var second = rppgSessionStore.claimRunningSessionId(rppgSessionId);

        assertThat(first).contains(runningSessionId);
        assertThat(second).isEmpty();
    }

    /** 측정을 끝내지 않고 앱을 꺼도 키가 영원히 남지 않아야 한다. */
    @Test
    void 저장된_키에는_만료_시간이_걸려_있다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        Long ttlSeconds = redisTemplate.getExpire(RppgSessionStore.KEY_PREFIX + rppgSessionId);

        assertThat(ttlSeconds).isNotNull().isPositive().isLessThanOrEqualTo(600);
    }
}
