package jungkathon3team.aftergrow.profile.dto;

import jakarta.validation.constraints.Min;
import jungkathon3team.aftergrow.profile.entity.UserGoal;

import java.time.LocalDateTime;

public class GoalUpdateDto {

    /**
     * R7 §7.2 PATCH /users/me/goal 요청. 부분 수정이라 두 필드 모두 nullable.
     * <p>{@code weeklyRunGoal}은 0 이상만 허용(음수는 E4001). null이면 검증을 건너뛰고 기존값 유지.
     */
    public record Request(
            String goalType,
            @Min(0) Integer weeklyRunGoal
    ) {
    }

    public record Response(
            String goalType,
            Integer weeklyRunGoal,
            LocalDateTime updatedAt
    ) {
        public static Response from(UserGoal g) {
            return new Response(g.getGoalType(), g.getWeeklyRunGoal(), g.getUpdatedAt());
        }
    }
}
