package jungkathon3team.aftergrow.heartrate.entity;

/**
 * rPPG 측정의 신호 품질. 워치(WATCH) 측정에는 해당 없음(null).
 * <p>POOR면 측정값을 신뢰할 수 없어 {@link SyncStatus#FAILED}로 저장하고 bpm/hrv를 버린다.
 */
public enum SignalQuality {
    GOOD,
    POOR
}
