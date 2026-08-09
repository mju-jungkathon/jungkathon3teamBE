package jungkathon3team.aftergrow.running.dto;

public record RunningPrepareResponse(
        String locationLabel,
        Integer uvIndex,
        String uvLevel,
        boolean goodTimeToRun,
        StretchingInfo stretching
) {
    public record StretchingInfo(
            String title,
            boolean optional,
            String description
    ) {
        public static StretchingInfo preRunDefault() {
            return new StretchingInfo("출발 전 스트레칭", true, "발목·종아리 위주 3분 루틴");
        }
    }
}

