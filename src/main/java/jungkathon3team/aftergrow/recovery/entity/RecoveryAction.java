package jungkathon3team.aftergrow.recovery.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * recovery_actions 테이블 매핑.
 * <p>가이드 하나에 "수분 보충", "쿨다운 스트레칭" 같은 액션이 여러 개 붙을 수 있어
 * RecoveryGuide와 별도 테이블로 정규화했다(ERD 참고).
 */
@Entity
@Table(name = "recovery_actions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RecoveryAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "action_id")
    private UUID actionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recovery_guide_id", nullable = false)
    private RecoveryGuide recoveryGuide;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50)
    private RecoveryActionType type;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    static RecoveryAction of(RecoveryGuide recoveryGuide, RecoveryActionType type, String title, String description) {
        return RecoveryAction.builder()
                .recoveryGuide(recoveryGuide)
                .type(type)
                .title(title)
                .description(description)
                .build();
    }
}