package jungkathon3team.aftergrow.heartrate.dto;

/**
 * R4.4 GET /heart-rate-measurements/rppg/guide
 * <p>고정 문구다. {@link #DURATION_SEC}는 R4.5 응답도 함께 쓴다 — 두 곳의 값이 달라지면 안 된다.
 */
public record RppgGuideResponse(
        String instruction,
        int durationSec
) {
    public static final String INSTRUCTION = "후면 카메라와 플래시에 손가락을 밀착시켜 약 12초간 측정해요";
    public static final int DURATION_SEC = 12;

    public static RppgGuideResponse defaults() {
        return new RppgGuideResponse(INSTRUCTION, DURATION_SEC);
    }
}
