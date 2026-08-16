# 🔧 AfterGrow — 백엔드 수정사항 정리

> 작성 기준: `API_명세서_AfterGrow.md` (2026-08-15, 실제 구현 기준) 대비 프론트엔드(`jungkathon3teamFE`) 요구사항 대조
> 목적: 프론트 목업 화면들을 실제 API로 연동하기 전, 백엔드에 먼저 반영돼야 하는 항목 정리
> 총 5개 항목 (신규 엔드포인트 2개, 기존 API 수정 2개, 설계 확정 1개)

---

## 한눈에 보기

| # | 항목 | 종류 | 우선순위 | 스키마 변경 |
|---|---|---|---|---|
| 1 | 시간대별 UV 예보 API | 신규 엔드포인트 | 높음 — 홈 화면 핵심 | 없음 (Redis만 사용) |
| 2 | 회원가입 약관 동의 저장 | 기존 API 수정 | 중간 — 법적 요건 | `USERS` 컬럼 3개 추가 |
| 3 | 러닝 경로(GPS 트랙) 저장 | 기존 API 수정 | 중간 — 지도 기능 필수 | `RUNNING_SESSIONS` 컬럼 1개 추가 |
| 4 | 카메라/위치 권한 상태 갱신 | 신규 엔드포인트 | 중간 — 권한 동기화 UX | 없음 |
| 5 | `goalType`/`weeklyRunGoal` 의미 재정의 | 기존 API 데이터 수정 | 확인 우선 | 없음 (컬럼은 이미 있음) |

---

## 1. 시간대별 UV 예보 — 신규 엔드포인트 필요

### 문제
현재 UV 관련 API는 두 개뿐이고 둘 다 홈 화면(`Home.jsx`)이 필요로 하는 데이터를 못 줌:

- `GET /running-sessions/prepare?lat&lng` → 그 순간의 UV **단일 값만**
- `GET /home` → `weeklySummary.cumulativeUvLevel` (이번 주 **평균**일 뿐, 오늘 시간대별 예보 아님)

프론트가 필요로 하는 건 세 가지인데 전부 빠져 있음:
1. 세션과 무관한 "지금 이 위치의 실시간 UV" (`TODAY'S UV 6`)
2. 시간대별 UV 예보 배열 (`UV_BY_HOUR`, 00시~22시 그래프)
3. UV가 낮은 추천 시간대 계산용 원본 데이터

### 왜 DB 테이블을 새로 만들면 안 되는가
UV 예보는 시간대 × 지역이라는 두 축으로 계속 변하는 데이터라, PostgreSQL에 저장하면:
- 유저별로 저장할 이유가 없는 "지리적 사실"을 계속 복제하게 됨
- 기상청이 이미 관리하는 원본 데이터를 우리 DB에 또 복제하는 셈

→ 팀이 이미 세운 원칙("재시작으로 사라져도 괜찮은 데이터는 Redis")에 정확히 해당. **테이블이 아니라 캐싱 전략의 공백.**

### 해결 방안 — Redis 캐시 + 격자좌표 키

```
GET /weather/uv-forecast?lat={lat}&lng={lng}

Response:
{
  "success": true,
  "data": {
    "hourly": [
      { "hour": "00", "uv": 0 },
      { "hour": "02", "uv": 0 },
      { "hour": "04", "uv": 1 },
      ...
      { "hour": "22", "uv": 0 }
    ]
  }
}
```

**동작 흐름**
1. 위경도(`lat`/`lng`) → 기상청 격자좌표(`nx`, `ny`) 변환 (`/prepare`에 이미 있는 LCC 투영 로직 재사용)
2. Redis 캐시 키 조회: `uv:forecast:{nx}:{ny}:{date}`
3. 캐시 히트 → 즉시 반환 (기상청 API 호출 없음)
4. 캐시 미스 → 기상청 API 1회 호출(하루치 전체 12개 시간대를 한 번에 받음) → Redis에 배열 통째로 저장(TTL 3~6시간)

**핵심 설계 포인트**
- 캐시 키에 **사용자 정보는 들어가지 않음** — 격자좌표는 순수 지리 정보라 로그인 여부와 무관하게 동작
- 같은 동네(반경 수 km) 사용자는 같은 격자로 떨어져 캐시를 자동 공유함
- 시간대별로 캐시를 쪼개지 않고 **하루치를 배열 하나로** 저장 — 기상청 API가 애초에 하루 전체를 한 번에 응답하므로 쪼개면 API 호출만 늘어남
- "지금 UV"와 "추천 시간대"도 이 배열 하나에서 가장 가까운 시간대를 골라 계산하면 되므로 별도 API 불필요

### 백엔드 기술 스택 문서 반영
Redis 사용처 목록에 추가:
```
- refresh token 저장
- /live 폴링 캐싱
- rate limiting
- UV 예보 캐싱 (격자좌표 기준, TTL 3~6h)  ← 신규
```

---

## 2. 회원가입 약관 동의 — 요청 body에 필드 자체가 없음

### 문제
`POST /auth/signup` 요청 body 확인 결과:
```json
{ "email": "...", "password": "...", "nickname": "..." }
```
`terms`(이용약관)/`privacy`(개인정보처리방침)/`marketing`(마케팅 수신) 관련 필드가 **아예 없음.** 프론트 `Auth.jsx`는 이 세 가지를 검증(`agreedReq = agree.terms && agree.privacy`)까지 하고 있는데 보낼 곳이 없는 상태.

### 왜 단순 boolean이 아니라 timestamp인가
- 이용약관/개인정보처리방침은 나중에 개정될 수 있고, "언제, 어떤 시점에 동의했는가"가 법적 증거로 필요할 수 있음
- 마케팅 동의는 유저가 프로필에서 나중에 껐다 켤 수 있는 값 — 현재 상태만으로는 이력이 안 남음
- `boolean`과 비교해 컬럼 개수·쿼리 복잡도는 거의 같으면서 "동의 시점"이 공짜로 따라옴

### 해결 방안

**API 수정**
```
POST /auth/signup 요청 body에 추가:
{
  "email": "...", "password": "...", "nickname": "...",
  "agreeTerms": true,
  "agreePrivacy": true,
  "agreeMarketing": false
}
```

**DB 마이그레이션**
```sql
-- V10__add_terms_agreement_to_users.sql
ALTER TABLE users ADD COLUMN terms_agreed_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE users ADD COLUMN privacy_agreed_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE users ADD COLUMN marketing_agreed_at TIMESTAMP NULL;
```

- `terms_agreed_at`/`privacy_agreed_at`: 필수 약관 → 항상 값 존재 (NULL이면 애초에 가입 불가 상태)
- `marketing_agreed_at`: 선택 약관 → NULL 허용, 값이 있으면 동의함/없으면 동의 안 함으로 판단

### 참고 — 별도 테이블은 지금 단계에서 불필요
약관 항목이 앞으로 계속 늘어나거나 버전별 재동의 이력을 전부 남겨야 하는 요구사항이 생기면 `TERMS_AGREEMENTS` 별도 테이블(user_id, terms_type, terms_version, agreed_at, withdrawn_at)로 분리하는 게 맞지만, 현재 규모(3개 고정 항목)에서는 오버엔지니어링. `USERS` 컬럼 3개로 충분.

---

## 3. 러닝 경로(GPS 트랙) — 저장할 필드가 없음

### 문제
`POST /running-sessions`(시작)와 `POST /running-sessions/{id}/end`(종료) 요청 body 둘 다 확인:
- 시작: `startedAt`, `location{lat,lng}`, `uvIndexAtStart`
- 종료: `endedAt`, `durationSec`, `distanceKm`, `intensity`

**시작 지점 좌표 1개만 저장되고, 이동 경로 전체(카카오맵에 그릴 폴리라인)를 저장할 필드가 없음.** History 화면의 `route`(경로 이름), `path`(지도에 그려질 궤적)는 현재 프론트 목업에서 SVG 문자열로 대체돼 있던 부분.

### 프론트 구현 방향 (참고)
```js
// 러닝 중 — GPS 좌표 실시간 수집
navigator.geolocation.watchPosition((pos) => {
  routePoints.push({ lat: pos.coords.latitude, lng: pos.coords.longitude, t: elapsedSec });
});

// 카카오맵에 그리기
const polyline = new kakao.maps.Polyline({
  path: routePoints.map(p => new kakao.maps.LatLng(p.lat, p.lng)),
  strokeWeight: 5, strokeColor: '#e4572e',
});
```
- 너무 촘촘하게 찍지 않도록 5~10초 간격 또는 일정 거리 이상일 때만 기록 (프론트 스로틀링)
- 서버 전송은 러닝 중이 아니라 **종료 시점에 한 번, 배열 통째로**

### 해결 방안

**API 수정**
```
POST /running-sessions/{id}/end 요청 body에 추가:
{
  "endedAt": "...", "durationSec": 1500, "distanceKm": 3.2, "intensity": "MODERATE",
  "routePath": [
    { "lat": 37.5440, "lng": 127.0557, "t": 0 },
    { "lat": 37.5442, "lng": 127.0559, "t": 8 },
    ...
  ]
}
```

**DB 마이그레이션**
```sql
-- V11__add_route_path_to_running_sessions.sql
ALTER TABLE running_sessions ADD COLUMN route_path JSONB;
```

### 왜 별도 테이블(`ROUTE_POINTS`)이 아니라 JSONB 컬럼인가

| 방식 | 장점 | 단점 |
|---|---|---|
| 별도 테이블 (점 하나당 행 하나) | 정규화됨, 구간별 통계 분석 용이 | 러닝 1회당 수백 건 INSERT, 조회 시 조인 필요 |
| **JSONB 컬럼 (채택)** | 구현 간단(INSERT/SELECT 한 번), 지도에 그릴 땐 어차피 배열 전체가 필요 | 점 단위 세부 분석은 나중에 어려움 |

해커톤 일정과, 어차피 프론트가 폴리라인을 그릴 땐 배열 전체가 한 번에 필요하다는 점을 고려해 JSONB 채택.

---

## 4. 카메라/위치 권한 상태 갱신 — 읽기만 있고 쓰기가 없음

### 문제
`GET /users/me/integrations`(6.3)는 있지만, 이 값을 갱신하는 `PATCH`가 명세서에 없음. `PATCH /users/me/goal`(6.2), `PATCH /users/me/notifications`(6.4)는 있는데 integrations만 쓰기 엔드포인트가 빠짐. `POST /integrations/apple-health/link`(4.3)가 `appleHealthLinked` 하나만 갱신할 뿐, `cameraPermission`/`locationPermission`은 갱신할 방법 자체가 없음.

### 왜 필요한가 — 서버 값은 "제어"가 아니라 "표시" 용도
브라우저 권한(GPS·카메라)은 사용자가 앱 밖(브라우저 설정)에서 언제든 바꿀 수 있어서, **서버 DB 값은 항상 "참고용 캐시"일 뿐 실제 권한 검증 수단이 될 수 없음.** 실제 기능 진입 시엔 항상 `navigator.geolocation`/`getUserMedia`를 다시 호출해서 그 순간의 성공/실패로 판단해야 하고, 그 결과를 서버에 동기화해서 Profile 화면에 "지금 상태"를 보여주는 용도로만 씀.

```js
// ❌ 나쁜 패턴 — 서버 값만 믿고 기능 진입
if (integrationStatus.cameraPermission) navigateToRppgScreen();

// ✅ 좋은 패턴 — 매번 실제로 시도, 결과를 서버에도 동기화
try {
  const stream = await navigator.mediaDevices.getUserMedia({ video: true });
  navigateToRppgScreen();
  syncIntegrationStatus({ cameraPermission: true });
} catch {
  showPermissionDeniedGuide();
  syncIntegrationStatus({ cameraPermission: false });
}
```

### 해결 방안

**신규 엔드포인트**
```
PATCH /users/me/integrations
{ "cameraPermission": true, "locationPermission": false }

Response:
{ "success": true, "data": { "locationLinked": true, "cameraPermission": true, "locationPermission": false, "appleHealthLinked": false } }
```
- `PATCH /users/me/notifications`와 동일하게 부분 수정(보낸 필드만 갱신) 방식 채택
- 스키마 변경 불필요 — `INTEGRATION_STATUS`는 이미 필요한 컬럼을 다 갖고 있음

---

## 5. `goalType`/`weeklyRunGoal` 의미 재정의

### 문제
`GET /users/me/profile` 응답 예시:
```json
"goal": { "goalType": "WEEKLY_DISTANCE", "weeklyRunGoal": 3 }
```
- 프론트 `Onboarding.jsx`의 `GOAL_TYPES`는 **운동 목적**(체력 증진/체중 감량/완주 훈련/스트레스 해소)을 의미
- API 예시값 `"WEEKLY_DISTANCE"`는 **목표 산정 기준(거리 vs 횟수)**을 뜻하는 것처럼 보여 완전히 다른 개념이 같은 필드에 섞여 있었음
- `weeklyRunGoal` 숫자 하나가 "3km"인지 "3회"인지도 불명확했음

### 확정된 해결 방향
`WEEKLY_DISTANCE`는 컬럼이 아니라 `goalType` 컬럼 안의 **값**이었으므로, 스키마 변경 없이 **역할만 재정의**하면 됨.

```json
"goal": {
  "goalType": "체력 증진",   // 순수하게 "목적"만 담당
  "weeklyRunGoal": 3         // "주 몇회" 횟수만 담당
}
```

| 필드 | 역할 | 값 |
|---|---|---|
| `goalType` | 운동 목적 | `체력 증진` \| `체중 감량` \| `완주 훈련` \| `스트레스 해소` (`GOAL_TYPES` 4개와 동일) |
| `weeklyRunGoal` | 주간 목표 **횟수** | 정수 (예: `3`) — km 아님, 명확히 횟수 |

**백엔드에 전달할 수정 요청**
```
1. goalType의 자유 문자열(VARCHAR) 후보값을 아래 4개로 확정
   "체력 증진" | "체중 감량" | "완주 훈련" | "스트레스 해소"
   (영문 enum 선호 시: FITNESS / WEIGHT_LOSS / RACE_TRAINING / STRESS_RELIEF)

2. weeklyRunGoal은 항상 "주간 러닝 횟수"만 의미함을 문서에 명시 (거리 아님)

3. "WEEKLY_DISTANCE" 값/개념은 goalType 후보에서 완전히 제거
```

**프론트 매핑도 단순해짐**
```
Onboarding.jsx
  type (체력 증진/체중 감량/완주 훈련/스트레스 해소)  →  goalType
  freq (1~7)                                          →  weeklyRunGoal
```

### 미해결로 남는 것 — 주간 "거리" 목표
`Home.jsx`의 "누적 거리 14.2 / 25.0km" 같은 **거리 기반 목표**는 이 재정의 이후에도 저장할 곳이 없음 (`weeklyRunGoal`은 이제 확실히 횟수 전용이므로). 아래 중 택일 필요:
- **A.** `weeklyDistanceGoalKm` 필드를 `USER_GOALS`에 추가로 신설
- **B.** 이번 스프린트에서는 "횟수 목표"만 쓰고, 홈 화면의 거리 목표 UI 자체를 제거

→ 팀 논의 후 결정 필요.

---

## 부록 — 이번에 다루지 않은 것 (참고용 재확인)

`API_명세서_AfterGrow.md` 부록 D에 이미 열린 이슈로 남아있는 것들은 이번 정리에서 별도로 다루지 않음:
- 배포 서버 Base URL 미확정
- `GET /running-sessions/prepare`에서 `lat`/`lng` 누락 시 에러 코드가 `500 E5000`으로 떨어질 가능성 (전용 핸들러 필요)

---

## 다음 액션

- [ ] 항목 1(UV 예보): `GET /weather/uv-forecast` 신설 + Redis 캐싱 구현
- [ ] 항목 2(약관 동의): `POST /auth/signup` body 확장 + `USERS` 컬럼 3개 마이그레이션(V10)
- [ ] 항목 3(경로 저장): `POST /running-sessions/{id}/end` body 확장 + `RUNNING_SESSIONS` 컬럼 1개 마이그레이션(V11)
- [ ] 항목 4(권한 갱신): `PATCH /users/me/integrations` 신설
- [ ] 항목 5(목표 필드): `goalType` 후보값 확정 + 문서 수정, 주간 거리 목표 A/B 결정
- [ ] 위 항목들이 반영된 새 API 명세서로 `docs/API.md` 갱신 → 프론트 `src/api/` 레이어 구축 시작
