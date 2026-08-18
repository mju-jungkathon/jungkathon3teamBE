package jungkathon3team.aftergrow.running.dto;

import java.time.LocalDate;

/** GET /running-sessions/weekly-count 응답. weekStart~weekEnd는 월~일(포함). */
public record WeeklyRunCountResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        long count
) {
}
