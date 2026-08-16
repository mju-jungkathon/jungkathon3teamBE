package jungkathon3team.aftergrow.profile.entity;

/**
 * 러닝 <b>목적</b>. 프론트 온보딩 화면의 선택지 4개와 1:1로 대응한다.
 * <p>과거 예시값 {@code WEEKLY_DISTANCE}는 목적이 아니라 "목표 산정 기준(거리 vs 횟수)"을 뜻해
 * 다른 개념이 같은 필드에 섞여 있었다. 후보에서 제거했고 V12가 기존 데이터를 정리한다.
 * <p>목표 <b>횟수</b>는 {@code UserGoal.weeklyRunGoal}이 담당한다(거리가 아니라 주 몇 회).
 */
public enum GoalType {

    /** 체력 증진 */
    FITNESS,

    /** 체중 감량 */
    WEIGHT_LOSS,

    /** 완주 훈련 */
    RACE_TRAINING,

    /** 스트레스 해소 */
    STRESS_RELIEF
}
