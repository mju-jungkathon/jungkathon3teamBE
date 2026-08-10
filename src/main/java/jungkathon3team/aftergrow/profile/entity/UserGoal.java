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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * user_goals 테이블 매핑. USERS와 1:1이며 {@code user_id}가 PK이자 FK.
 * <p>R2(홈 대시보드)에서는 {@code weekly_run_goal}만 사용한다. R7(프로필/목표 수정)에서 확장 예정.
 * <p>{@code goal_type}은 명세상 enum 후보지만 아직 사용처가 없어 String으로 둔다.
 */
@Entity
@Table(name = "user_goals")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserGoal {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "goal_type", length = 50)
    private String goalType;

    @Column(name = "weekly_run_goal")
    private Integer weeklyRunGoal;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
