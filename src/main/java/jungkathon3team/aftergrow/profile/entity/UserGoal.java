package jungkathon3team.aftergrow.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** 러닝 목적. 온보딩 전에는 아직 고르지 않았을 수 있어 null 허용. */
    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 50)
    private GoalType goalType;

    /** 주간 목표 <b>횟수</b>(주 몇 회). 거리가 아니다 — 거리 목표는 아직 저장할 곳이 없다. */
    @Column(name = "weekly_run_goal")
    private Integer weeklyRunGoal;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 설정 행이 없던 사용자가 처음 목표를 저장할 때 생성한다(upsert). */
    public static UserGoal create(UUID userId) {
        return UserGoal.builder()
                .userId(userId)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * R7 §7.2 부분 수정. null로 온(요청에서 생략된) 필드는 기존값을 유지한다.
     * <p>{@code updated_at}은 응답에 즉시 실려야 하므로 JPA 생명주기 콜백(flush 시점) 대신
     * 이 메서드에서 직접 갱신한다.
     */
    public void updatePartial(GoalType goalType, Integer weeklyRunGoal) {
        if (goalType != null) {
            this.goalType = goalType;
        }
        if (weeklyRunGoal != null) {
            this.weeklyRunGoal = weeklyRunGoal;
        }
        this.updatedAt = LocalDateTime.now();
    }
}
