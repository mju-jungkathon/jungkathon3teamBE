package jungkathon3team.aftergrow.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * integration_status 테이블 매핑. USERS와 1:1이며 {@code user_id}가 PK이자 FK.
 * <p>R4.3(애플 헬스 연동 기록)에서는 {@code apple_health_linked}만 사용하지만,
 * R7(프로필/설정)이 같은 테이블을 다시 열기 때문에 네 컬럼을 모두 매핑해 둔다.
 * <p>DB 컬럼이 모두 {@code NOT NULL DEFAULT false}라 필드는 원시 타입 {@code boolean}이다.
 */
@Entity
@Table(name = "integration_status")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntegrationStatus {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "location_linked", nullable = false)
    private boolean locationLinked;

    @Column(name = "camera_permission", nullable = false)
    private boolean cameraPermission;

    @Column(name = "location_permission", nullable = false)
    private boolean locationPermission;

    @Column(name = "apple_health_linked", nullable = false)
    private boolean appleHealthLinked;

    /** 연동 정보가 아직 없는 사용자의 새 행. 모든 플래그가 꺼진 상태다. */
    public static IntegrationStatus of(UUID userId) {
        return IntegrationStatus.builder()
                .userId(userId)
                .build();
    }

    /**
     * R4.3. 앱이 HealthKit 권한 동의 결과를 알려올 때 호출한다.
     * 사용자가 iOS 설정에서 권한을 회수하면 false로도 들어온다.
     */
    public void linkAppleHealth(boolean linked) {
        this.appleHealthLinked = linked;
    }
}
