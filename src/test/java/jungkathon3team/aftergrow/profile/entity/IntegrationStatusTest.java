package jungkathon3team.aftergrow.profile.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationStatusTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void 새로_만들면_모든_연동이_꺼져_있다() {
        IntegrationStatus status = IntegrationStatus.of(userId);

        assertThat(status.getUserId()).isEqualTo(userId);
        assertThat(status.isAppleHealthLinked()).isFalse();
        assertThat(status.isLocationLinked()).isFalse();
        assertThat(status.isCameraPermission()).isFalse();
        assertThat(status.isLocationPermission()).isFalse();
    }

    @Test
    void 애플_헬스_연동을_켤_수_있다() {
        IntegrationStatus status = IntegrationStatus.of(userId);

        status.linkAppleHealth(true);

        assertThat(status.isAppleHealthLinked()).isTrue();
    }

    /** 사용자가 iOS 설정에서 권한을 회수하면 앱이 false로 다시 보낸다. */
    @Test
    void 애플_헬스_연동을_끌_수_있다() {
        IntegrationStatus status = IntegrationStatus.of(userId);
        status.linkAppleHealth(true);

        status.linkAppleHealth(false);

        assertThat(status.isAppleHealthLinked()).isFalse();
    }

    @Test
    void 애플_헬스_연동은_다른_연동_상태를_건드리지_않는다() {
        IntegrationStatus status = IntegrationStatus.of(userId);

        status.linkAppleHealth(true);

        assertThat(status.isLocationLinked()).isFalse();
        assertThat(status.isCameraPermission()).isFalse();
        assertThat(status.isLocationPermission()).isFalse();
    }
}
