-- 러닝 이동 경로(GPS 트랙). 프론트가 카카오맵 폴리라인을 그릴 때 배열 전체를 한 번에 쓰므로
-- 점 하나당 행 하나인 별도 테이블 대신 JSONB 한 컬럼에 통째로 저장한다.
-- 러닝 1회당 수백 건 INSERT와 조회 시 조인이 사라지는 대신, 점 단위 구간 분석은 나중에 어려워진다.
--
-- 기존 세션에는 경로가 없으므로 NULL 허용.
ALTER TABLE running_sessions ADD COLUMN route_path JSONB;
