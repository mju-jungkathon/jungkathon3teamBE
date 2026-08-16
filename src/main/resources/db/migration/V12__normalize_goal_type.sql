-- goal_type이 자유 문자열이던 시절의 값을 GoalType enum 후보 4개로 정리한다.
-- 정리하지 않으면 조회 시 Hibernate가 enum 변환에 실패해 500이 난다.
--
-- WEEKLY_DISTANCE는 "목적"이 아니라 "목표 산정 기준"이라 옮겨 담을 후보가 없다 —
-- 사용자가 온보딩에서 다시 고르도록 NULL로 비운다(목적 미설정 상태는 원래 허용된다).
UPDATE user_goals
SET goal_type = NULL
WHERE goal_type IS NOT NULL
  AND goal_type NOT IN ('FITNESS', 'WEIGHT_LOSS', 'RACE_TRAINING', 'STRESS_RELIEF');
