package jungkathon3team.aftergrow.profile.dto;

import jungkathon3team.aftergrow.profile.entity.GoalType;
import jungkathon3team.aftergrow.profile.entity.IntegrationStatus;
import jungkathon3team.aftergrow.profile.entity.NotificationSetting;
import jungkathon3team.aftergrow.profile.entity.UserGoal;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * R7 §7.1 GET /users/me/profile 응답.
 * <p>설정 행이 없는 사용자도 화면이 뜨도록, goal/integrations/notifications 객체는 항상 존재하되
 * 내부 값이 비어 있을 수 있다(목표/알림은 필드 null, 연동은 전부 false).
 */
public record ProfileResponse(
        String nickname,
        Goal goal,
        Integrations integrations,
        Notifications notifications
) {

    /** {@code goalType}은 운동 목적, {@code weeklyRunGoal}은 주간 <b>횟수</b>다(거리 아님). */
    public record Goal(GoalType goalType, Integer weeklyRunGoal) {
        public static Goal from(UserGoal g) {
            return g == null
                    ? new Goal(null, null)
                    : new Goal(g.getGoalType(), g.getWeeklyRunGoal());
        }
    }

    public record Integrations(
            boolean locationLinked,
            boolean cameraPermission,
            boolean locationPermission,
            boolean appleHealthLinked
    ) {
        public static Integrations from(IntegrationStatus s) {
            return s == null
                    ? new Integrations(false, false, false, false)
                    : new Integrations(s.isLocationLinked(), s.isCameraPermission(),
                    s.isLocationPermission(), s.isAppleHealthLinked());
        }
    }

    public record Notifications(
            LocalTime runningReminderTime,
            DayOfWeek weeklyReportDay,
            LocalTime weeklyReportTime
    ) {
        public static Notifications from(NotificationSetting s) {
            return s == null
                    ? new Notifications(null, null, null)
                    : new Notifications(s.getRunningReminderTime(), s.getWeeklyReportDay(), s.getWeeklyReportTime());
        }
    }

    public static ProfileResponse of(String nickname, UserGoal goal, IntegrationStatus integration,
                                     NotificationSetting notification) {
        return new ProfileResponse(
                nickname,
                Goal.from(goal),
                Integrations.from(integration),
                Notifications.from(notification)
        );
    }
}
