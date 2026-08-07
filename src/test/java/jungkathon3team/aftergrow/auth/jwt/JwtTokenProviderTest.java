package jungkathon3team.aftergrow.auth.jwt;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-algorithm";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3_600_000L, 2_592_000_000L);

    @Test
    void access_토큰에서_userId를_다시_꺼낼_수_있다() {
        UUID userId = UUID.randomUUID();

        assertThat(provider.parseAccessToken(provider.createAccessToken(userId)))
                .isEqualTo(userId);
    }

    @Test
    void refresh_토큰을_access_자리에_쓰면_거부된다() {
        String refreshToken = provider.createRefreshToken(UUID.randomUUID());

        assertThatThrownBy(() -> provider.parseAccessToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 다른_키로_서명된_토큰은_거부된다() {
        JwtTokenProvider attacker =
                new JwtTokenProvider("completely-different-secret-key-also-long-enough!!", 3_600_000L, 1L);
        String forged = attacker.createAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> provider.parseAccessToken(forged))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 만료된_토큰은_거부된다() throws InterruptedException {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 1L, 1L);
        String token = shortLived.createAccessToken(UUID.randomUUID());
        Thread.sleep(50);

        assertThatThrownBy(() -> shortLived.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 형식이_아닌_문자열은_거부된다() {
        assertThatThrownBy(() -> provider.parseAccessToken("not-a-jwt"))
                .isInstanceOf(BusinessException.class);
    }
}
