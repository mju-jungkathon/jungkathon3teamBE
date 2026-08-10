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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * notification_settings 테이블 매핑. USERS와 1:1이며 {@code user_id}가 PK이자 FK.
 * <p>{@code weekly_report_day}는 명세상 영문 요일("SUNDAY")이라 {@link DayOfWeek} enum으로 매핑한다.
 */
@Entity
@Table(name = "notification_settings")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationSetting {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "running_reminder_time")
    private LocalTime runningReminderTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekly_report_day", length = 20)
    private DayOfWeek weeklyReportDay;

    @Column(name = "weekly_report_time")
    private LocalTime weeklyReportTime;

    /** 설정 행이 없던 사용자가 처음 알림을 저장할 때 생성한다(upsert). */
    public static NotificationSetting create(UUID userId) {
        return NotificationSetting.builder()
                .userId(userId)
                .build();
    }

    /**
     * R7 §7.4 부분 수정. null로 온(요청에서 생략된) 필드는 기존값을 유지한다.
     */
    public void updatePartial(LocalTime runningReminderTime, DayOfWeek weeklyReportDay, LocalTime weeklyReportTime) {
        if (runningReminderTime != null) {
            this.runningReminderTime = runningReminderTime;
        }
        if (weeklyReportDay != null) {
            this.weeklyReportDay = weeklyReportDay;
        }
        if (weeklyReportTime != null) {
            this.weeklyReportTime = weeklyReportTime;
        }
    }
}
