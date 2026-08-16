package jungkathon3team.aftergrow.profile.dto;

import jakarta.validation.constraints.Min;
import jungkathon3team.aftergrow.profile.entity.GoalType;
import jungkathon3team.aftergrow.profile.entity.UserGoal;

import java.time.LocalDateTime;

public class GoalUpdateDto {

    /**
     * R7 §7.2 PATCH /users/me/goal 요청. 부분 수정이라 두 필드 모두 nullable.
     * <p>{@code goalType}은 운동 <b>목적</b>({@link GoalType} 4개 중 하나), {@code weeklyRunGoal}은
     * 주간 <b>횟수</b>다(거리 아님). 후보 밖 문자열은 역직렬화 단계에서 E4001로 거절된다.
     * <p>{@code weeklyRunGoal}은 0 이상만 허용(음수는 E4001). null이면 검증을 건너뛰고 기존값 유지.
     */
    public record Request(
            GoalType goalType,
            @Min(0) Integer weeklyRunGoal
    ) {
    }

    public record Response(
            GoalType goalType,
            Integer weeklyRunGoal,
            LocalDateTime updatedAt
    ) {
        public static Response from(UserGoal g) {
            return new Response(g.getGoalType(), g.getWeeklyRunGoal(), g.getUpdatedAt());
        }
    }
}
