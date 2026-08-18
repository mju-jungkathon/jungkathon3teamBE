package jungkathon3team.aftergrow.running.entity;

import java.util.List;

public enum RunningStatus {
    IN_PROGRESS,
    ENDED,
    COMPLETED;

    /** 집계에서 "완료"로 인정하는 상태(ENDED+COMPLETED). 홈/러닝 주간 집계가 공유한다. */
    public static final List<RunningStatus> COMPLETED_STATUSES = List.of(ENDED, COMPLETED);
}
