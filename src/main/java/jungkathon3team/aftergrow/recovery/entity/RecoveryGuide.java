package jungkathon3team.aftergrow.recovery.entity;

import jakarta.persistence.*;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * recovery_guides 테이블 매핑. RunningSession : RecoveryGuide = 1 : 0..1
 * (running_session_id UNIQUE — 세션당 가이드는 최대 하나).
 * <p>R5.1에서만 생성된다. 이미 생성된 세션에 다시 요청이 오면 재생성하지 않고
 * 기존 행을 그대로 돌려준다({@code RecoveryGuideService.generate} 참고).
 */
@Entity
@Table(name = "recovery_guides")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RecoveryGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "recovery_guide_id")
    private UUID recoveryGuideId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_session_id", nullable = false, unique = true)
    private RunningSession runningSession;

    @Column(name = "measured_bpm")
    private Integer measuredBpm;

    @Column(name = "summary_message", columnDefinition = "TEXT")
    private String summaryMessage;

    @Column(name = "cooldown_timer_sec")
    private Integer cooldownTimerSec;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "recoveryGuide", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecoveryAction> actions = new ArrayList<>();

    public static RecoveryGuide create(RunningSession runningSession,
                                       Integer measuredBpm,
                                       String summaryMessage,
                                       Integer cooldownTimerSec) {
        return RecoveryGuide.builder()
                .runningSession(runningSession)
                .measuredBpm(measuredBpm)
                .summaryMessage(summaryMessage)
                .cooldownTimerSec(cooldownTimerSec)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void addAction(RecoveryActionType type, String title, String description) {
        this.actions.add(RecoveryAction.of(this, type, title, description));
    }

    public boolean isOwnedBy(UUID userId) {
        return this.runningSession.isOwnedBy(userId);
    }
}
