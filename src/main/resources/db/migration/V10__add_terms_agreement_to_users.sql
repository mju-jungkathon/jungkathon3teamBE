-- 회원가입 약관 동의 시점 기록.
-- boolean이 아니라 TIMESTAMP인 이유: 약관은 개정될 수 있어 "언제 동의했는가"가 법적 증거로 필요하다.
-- 기존 행은 DEFAULT now()로 채워진다(가입 시점을 알 수 없는 레거시 행이라 마이그레이션 시각이 들어간다).
ALTER TABLE users ADD COLUMN terms_agreed_at     TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE users ADD COLUMN privacy_agreed_at   TIMESTAMP NOT NULL DEFAULT now();

-- 선택 약관. NULL = 동의 안 함, 값 있음 = 그 시점에 동의함.
ALTER TABLE users ADD COLUMN marketing_agreed_at TIMESTAMP;
