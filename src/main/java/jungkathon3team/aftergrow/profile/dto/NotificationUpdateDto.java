package jungkathon3team.aftergrow.profile.dto;

import jungkathon3team.aftergrow.profile.entity.NotificationSetting;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class NotificationUpdateDto {

    /**
     * R7 §7.4 PATCH /users/me/notifications 요청. 부분 수정이라 세 필드 모두 nullable.
     * <p>시간은 "HH:mm"("07:00") 형태를 {@link LocalTime}으로, 요일은 영문("SUNDAY")을 {@link DayOfWeek}로 받는다.
     */
    public record Request(
            LocalTime runningReminderTime,
            DayOfWeek weeklyReportDay,
            LocalTime weeklyReportTime
    ) {
    }

    public record Response(
            LocalTime runningReminderTime,
            DayOfWeek weeklyReportDay,
            LocalTime weeklyReportTime
    ) {
        public static Response from(NotificationSetting s) {
            return new Response(s.getRunningReminderTime(), s.getWeeklyReportDay(), s.getWeeklyReportTime());
        }
    }
}
