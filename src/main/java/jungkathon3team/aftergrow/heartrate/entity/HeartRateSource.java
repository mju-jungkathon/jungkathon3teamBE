package jungkathon3team.aftergrow.heartrate.entity;

/**
 * 심박수 측정 방식.
 * <p>API 명세 §0 "측정 방식(Enum)" 기준: WATCH(애플 헬스 연동) / RPPG(후면 카메라 손가락 측정).
 */
public enum HeartRateSource {
    WATCH,
    RPPG
}
