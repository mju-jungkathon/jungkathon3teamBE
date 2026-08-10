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
 * <p>R7 §7.3은 조회만 있고 수정 API가 없다. 실제 연동/권한 값은 후속(R4 애플헬스 연동, 권한 API)에서
 * 이 테이블에 써줘야 채워진다. 그 전까지 행이 없으면 서비스가 {@link #defaults(UUID)}(전부 false)로 응답한다.
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

    /** 아직 연동 행이 없는 사용자에게 돌려줄 기본값(전부 false). DB에 저장하지 않는다. */
    public static IntegrationStatus defaults(UUID userId) {
        return IntegrationStatus.builder()
                .userId(userId)
                .locationLinked(false)
                .cameraPermission(false)
                .locationPermission(false)
                .appleHealthLinked(false)
                .build();
    }
}
