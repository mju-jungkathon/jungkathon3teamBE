package jungkathon3team.aftergrow.recovery.entity;

/**
 * 회복 가이드 액션 종류. DB 컬럼은 VARCHAR(50) 자유 문자열이라 값을 바꿔도 마이그레이션이 필요 없다.
 * <p>{@code docs/피부회복가이드_프롬프트.md}의 6분류를 그대로 따른다 — 세안·진정·보습을 하나로
 * 뭉치지 않고 나눠야 "지금 이 상황엔 뭐가 필요한지"를 LLM이 더 구체적으로 판단한다.
 */
public enum RecoveryActionType {
    HYDRATION,
    /** 심박수를 진정시킨 뒤 스킨케어를 시작하라는 안내. 근육 스트레칭이 아니다. */
    COOLDOWN,
    CLEANSING,
    /** 홍조·열감 진정. UV_CARE에 이미 진정 내용이 담기면 생략된다. */
    SOOTHING,
    UV_CARE,
    MOISTURIZING
}
