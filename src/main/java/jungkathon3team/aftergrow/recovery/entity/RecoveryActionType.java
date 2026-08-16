package jungkathon3team.aftergrow.recovery.entity;

/**
 * 회복 가이드 액션 종류.
 * <p>API 명세 예시(§5.1)에는 HYDRATION / COOLDOWN_STRETCH만 등장하지만,
 * DB 컬럼이 VARCHAR(50) 자유 문자열이라 새 타입을 추가해도 마이그레이션이 필요 없다.
 * UV_CAUTION은 명세에 없는 값으로, 러닝 시작 시점 UV 지수가 높았을 때만 추가된다.
 */
public enum RecoveryActionType {
    HYDRATION,
    COOLDOWN_STRETCH,
    UV_CAUTION,
    /** 러닝 직후 스킨케어(세안·진정·보습·자외선 차단). LLM이 운동 데이터를 보고 구체 문구를 만든다. */
    SKINCARE
}
