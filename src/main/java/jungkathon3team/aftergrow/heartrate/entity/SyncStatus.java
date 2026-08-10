package jungkathon3team.aftergrow.heartrate.entity;

/**
 * 심박수 측정 기록의 동기화 상태.
 * <p>API 명세 R4.6 / R6.1 기준. 측정 중 상태(PENDING)는 두지 않는다 —
 * rPPG 측정 중에는 Redis({@code rppg:{id}})만 존재하고 DB에는 행이 만들어지지 않는다.
 */
public enum SyncStatus {
    SUCCESS,
    FAILED
}
