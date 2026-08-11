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
 * <p>DB 컬럼이 모두 {@code NOT NULL DEFAULT false}라 필드는 원시 타입 {@code boolean}이다.
 * <p><b>두 도메인이 함께 쓴다.</b> R7(프로필/설정) §7.3은 조회만 하고 수정 API가 없으며,
 * 실제 값을 쓰는 쪽은 R4.3(애플 헬스 연동 기록)이다. R7이 "행이 없으면 전부 false로 응답"하는 것은
 * R4.3이 아직 행을 만들지 않았을 때의 동작이다.
 * <p>정적 팩토리가 둘인 이유는 <b>저장 여부</b>가 갈리기 때문이다 — {@link #defaults(UUID)}는 응답 전용이라
 * DB에 저장하지 않고, {@link #of(UUID)}는 곧 저장될 새 행이다. 만들어지는 객체 자체는 같다.
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

    /**
     * 아직 연동 행이 없는 사용자에게 돌려줄 기본값(전부 false). <b>DB에 저장하지 않는다.</b>
     * <p>R7 프로필 조회 응답용. 저장할 새 행이 필요하면 {@link #of(UUID)}를 쓴다.
     */
    public static IntegrationStatus defaults(UUID userId) {
        return IntegrationStatus.builder()
                .userId(userId)
                .locationLinked(false)
                .cameraPermission(false)
                .locationPermission(false)
                .appleHealthLinked(false)
                .build();
    }

    /**
     * 연동 정보가 아직 없는 사용자의 <b>새 행</b>. 모든 플래그가 꺼진 상태다.
     * <p>R4.3이 {@link #linkAppleHealth(boolean)}로 값을 채운 뒤 저장한다.
     * 저장하지 않고 응답에만 쓸 값이면 {@link #defaults(UUID)}를 쓴다.
     */
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
