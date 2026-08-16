# API 명세

---

## 0. 공통 사항

### Base URL

```
로컬 개발: http://localhost:8080
운영:      미정 (AWS 인프라 구성 후 확정)
```

- **버전 프리픽스(`/v1`)는 사용하지 않습니다.** 배포된 클라이언트가 없어 버전 분리 대상이 없습니다. 필요해지면 서버 설정 한 줄로 추가하고 이 문서를 갱신합니다.
- 따라서 회원가입은 `http://localhost:8080/auth/signup`입니다.

| **화면 번호** | **화면명** | **주요 기능 및 HTTP 메서드 & 경로** |
| --- | --- | --- |
| **1** | **로그인 / 회원가입** | • `POST /auth/signup`

• `POST /auth/login`

• `POST /auth/refresh` |
| **2** | **홈 대시보드** | • `GET /home` *(오늘 러닝 상태, 주간 요약 등)* |
| **3** | **러닝 준비** | • `GET /running-sessions/prepare?lat={lat}&lng={lng}`

• `POST /stretching-sessions`

• `POST /running-sessions` *(러닝 시작)* |
| **4** | **러닝 진행 중** | • `GET /running-sessions/{id}/live`

• `POST /running-sessions/{id}/end` |
| **5** | **심박수 확인 (워치)** | • `POST /running-sessions/{id}/heart-rate/select-source`

• `POST /integrations/apple-health/heart-rate`

• `POST /integrations/apple-health/link` |
| **6** | **심박수 확인 (rPPG)** | • `GET /heart-rate-measurements/rppg/guide`

• `POST /heart-rate-measurements/rppg/start`

• `POST /heart-rate-measurements/rppg/{id}/result` |
| **7** | **회복 가이드** | • `POST /running-sessions/{id}/recovery-guide`

• `POST /recovery-guides/{id}/cooldown-timer/start`

• `POST /running-sessions/{id}/complete` |
| **8** | **측정 기록** | • `GET /heart-rate-measurements?range=30d`

• `POST /heart-rate-measurements/{id}/retry` |
| **9** | **프로필 & 설정** | • `GET /users/me/profile`

• `PATCH /users/me/goal`

• `GET /users/me/integrations`

• `PATCH /users/me/notifications`

• `POST /auth/logout` |

### 인증

- `Authorization: Bearer {access_token}` (JWT, 로그인 이후 모든 요청에 필요)

### 공통 응답 포맷

**모든 응답은 성공/실패 관계없이 아래 세 필드로 감쌉니다.** `success`, `data`, `error`는 항상 존재합니다.

성공:

```json
{ "success": true, "data": { }, "error": null }
```

실패:

```json
{ "success": false, "data": null, "error": { "code": "E4001", "message": "요청 값이 유효하지 않습니다." } }
```

> ⚠️ **이 문서의 각 엔드포인트 "Response" 예시는 `data` 안쪽 내용만 표기합니다.**
> 지면을 아끼기 위한 표기이며, 실제 응답에는 항상 위 래퍼가 씌워집니다.
>
> 예를 들어 §1.1 회원가입의 `Response 201`이 이렇게 적혀 있으면
>
> ```json
> { "userId": "uuid", "email": "you@example.com" }
> ```
>
> 실제로 내려가는 본문은 이렇습니다.
>
> ```json
> { "success": true, "data": { "userId": "uuid", "email": "you@example.com" }, "error": null }
> ```
>
> 클라이언트는 응답 인터셉터에서 `data`를 한 번 벗겨 쓰면 됩니다.

**예외**: `204 No Content` 응답(§1.4 로그아웃)은 본문 자체가 없으므로 래퍼도 없습니다.

### 공통 에러 코드

`message`는 서버 기본 문구이며, 상황에 따라 더 구체적인 문구로 대체될 수 있습니다. **클라이언트 분기는 `message`가 아니라 `code`로 하세요.**

| 코드 | HTTP Status | 설명 | 기본 message |
| --- | --- | --- | --- |
| E4001 | 400 | 요청 값 검증 실패 | 요청 값이 유효하지 않습니다. |
| E4010 | 401 | 인증 토큰 없음/만료/위조 | 인증 토큰이 없거나 만료되었습니다. |
| E4011 | 401 | 로그인 자격 증명 불일치 | 이메일 또는 비밀번호가 올바르지 않습니다. |
| E4030 | 403 | 권한 없음 (예: 카메라/위치 권한 미허용 상태에서 측정 요청) | 권한이 없습니다. |
| E4040 | 404 | 리소스 없음 (세션/측정 기록 없음) | 요청한 리소스를 찾을 수 없습니다. |
| E4090 | 409 | 이미 진행 중인 러닝 세션 존재 | 이미 진행 중인 러닝 세션이 있습니다. |
| E4091 | 409 | 이미 가입된 이메일 (회원가입) | 이미 사용 중인 이메일입니다. |
| E5000 | 500 | 서버 오류 | 서버 오류가 발생했습니다. |
| E5010 | 502 | 애플 헬스 연동 실패 | 애플 헬스 데이터를 가져오지 못했습니다. |
| E5011 | 502 | 자외선 예보(기상청) 연동 실패 | 자외선 예보를 가져오지 못했습니다. |

> 📌 코드는 대체로 `E{HTTP 상태코드}{일련번호}` 형태지만 **`E5010`만 502를 가리켜 규칙에서 벗어납니다.**
> 상태코드 502가 의미상 맞아(외부 서비스 연동 실패) 서버는 이 표를 그대로 구현했습니다.
> 코드값이 오타(`E5020`)인지 확인이 필요하며, 바꾼다면 서버의 `ErrorCode`와 `ErrorCodeTest`도 함께 수정해야 합니다.

`@Valid` 검증 실패 시에는 `E4001`에 위반한 필드의 메시지가 담겨 내려갑니다.

### 날짜/시각 표기

이 문서의 예시는 `2026-08-04T09:00:00+09:00`처럼 오프셋을 붙여 적었지만, **현재 서버는 오프셋 없는 ISO 로컬 시각으로 내려줍니다.**

```json
"createdAt": "2026-08-07T16:22:15.4986088"
```

서버 엔티티가 `LocalDateTime`(DB는 `TIMESTAMP`)이라 타임존 정보를 담지 않기 때문입니다. 서버와 사용자가 모두 KST라 당장 문제는 없지만, 오프셋이 필요하면 엔티티를 `OffsetDateTime`으로 바꾸고 마이그레이션(`TIMESTAMP` → `TIMESTAMPTZ`)을 추가해야 합니다. **클라이언트는 받은 문자열을 KST로 해석하세요.**

### 측정 방식(Enum)

- `heartRateSource`: `WATCH`(애플 헬스 연동) / `RPPG`(후면 카메라 손가락 측정)
- `syncStatus`: `SUCCESS` / `FAILED`
- `intensity`: `LOW` / `MODERATE` / `HIGH`

---

## R1. 계정 인증 (화면 1)

### 1.1 회원가입

`POST /auth/signup`

**Request**

```json
{
  "email": "you@example.com",
  "password": "string",
  "nickname": "김러너",
  "agreeTerms": true,
  "agreePrivacy": true,
  "agreeMarketing": false
}
```

- `email`: 필수, 이메일 형식, 255자 이하
- `password`: 필수, 8자 이상 64자 이하 (BCrypt 해시로 저장, 응답에 절대 포함되지 않음)
- `nickname`: 필수, 100자 이하
- `agreeTerms` / `agreePrivacy`: **필수 약관.** `true`가 아니거나 생략하면 400 `E4001`
- `agreeMarketing`: 선택. 생략하면 동의하지 않은 것으로 봅니다

> 서버는 boolean이 아니라 **동의 시각**(`terms_agreed_at` 등)을 저장합니다. 약관은 개정될 수 있어
> "언제 동의했는가"가 필요하기 때문입니다. 마케팅 동의는 값이 있으면 동의, `NULL`이면 미동의입니다.
> 동의 시각은 응답에 포함되지 않습니다.

**Response 201**

```json
{ "userId": "uuid", "email": "you@example.com", "nickname": "김러너", "createdAt": "2026-08-07T16:22:15.4986088" }
```

**에러**

| 상황 | 코드 |
| --- | --- |
| 이미 가입된 이메일 | 409 `E4091` |
| 요청 값 검증 실패 | 400 `E4001` (위반 필드의 메시지가 `error.message`에 담김) |

### 1.2 로그인

`POST /auth/login`

**Request**

```json
{ "email": "you@example.com", "password": "string" }
```

**Response 200**

```json
{ "accessToken": "jwt", "refreshToken": "jwt", "expiresIn": 3600 }
```

- `expiresIn`: access 토큰 만료까지 남은 **초**. 현재 3600(1시간)
- `refreshToken`은 서버(Redis)에도 저장되어 로그아웃 시 즉시 무효화할 수 있습니다. **사용자당 하나만 유지**되므로 다시 로그인하면 이전 refresh 토큰은 무효가 됩니다
- 두 토큰은 서로 자리를 바꿔 쓸 수 없습니다. refresh 토큰을 `Authorization` 헤더에 넣으면 401(`E4010`)입니다

**에러**

| 상황 | 코드 |
| --- | --- |
| 이메일 또는 비밀번호 불일치 | 401 `E4011` |
| 요청 값 검증 실패 (빈 값) | 400 `E4001` |

> 🔒 **가입되지 않은 이메일과 비밀번호 오류는 응답이 완전히 동일합니다.** 구분해서 알려주면 어떤 이메일이 가입돼 있는지 알아낼 수 있기 때문입니다. 클라이언트도 두 경우를 나눠 안내하지 마세요.

### 1.3 토큰 재발급

`POST /auth/refresh`

**Request**

```json
{ "refreshToken": "jwt" }
```

**Response 200**

```json
{ "accessToken": "jwt", "expiresIn": 3600 }
```

- **응답에 refreshToken은 포함되지 않습니다.** 기존 refresh 토큰을 계속 사용하세요.
- access 토큰이 만료(401 `E4010`)되면 이 엔드포인트로 재발급받고 원래 요청을 재시도하는 흐름을 권장합니다.

**에러**

| 상황 | 코드 |
| --- | --- |
| 서명 위조 · 만료 · access 토큰을 넣은 경우 | 401 `E4010` |
| **로그아웃했거나 재로그인으로 교체된 토큰** | 401 `E4010` |
| `refreshToken` 누락 | 400 `E4001` |

> 서명 검증만 통과하면 되는 게 아니라 **서버(Redis)에 저장된 값과 일치**해야 합니다. 그래서 로그아웃과 재로그인이 즉시 반영됩니다. 401을 받으면 로그인 화면으로 보내세요.

### 1.4 로그아웃 (화면 9)

`POST /auth/logout`

- **`Authorization: Bearer {accessToken}` 필요.** 누구를 로그아웃시킬지 토큰에서 판단합니다. 요청 본문은 없습니다.
- 저장된 refresh 토큰을 삭제해 더 이상 재발급되지 않게 합니다.
- 같은 요청을 여러 번 보내도 결과는 같습니다(204).

**Response 204** — 본문 없음

> ⚠️ **이미 발급된 access 토큰은 만료 전(최대 1시간)까지 유효합니다.** JWT는 취소할 수 없기 때문이며, access 수명을 짧게 잡은 이유가 이 창을 좁히기 위함입니다. 클라이언트는 로그아웃 시 저장소(Keychain 등)에서 토큰 두 개를 모두 지워주세요.

**에러**

| 상황 | 코드 |
| --- | --- |
| 토큰 없음 · 유효하지 않음 | 401 `E4010` |

---

## R2. 홈 대시보드 (화면 2)

### 2.1 홈 요약 조회

`GET /home`

**Response 200**

```json
{
  "greeting": "안녕하세요, 김러너님",
  "weeklyRunCount": 3,
  "weeklyGoalCount": 5,
  "remainingToGoal": 2,
  "latestMeasurement": {
    "heartRateSource": "RPPG",
    "avgBpm": 146,
    "measuredAt": "2026-08-04T07:42:00+09:00"
  },
  "todayRunningStatus": "NOT_STARTED",
  "weeklySummary": {
    "totalDistanceKm": 14.2,
    "avgBpm": 149,
    "cumulativeUvLevel": "보통"
  }
}
```

- `todayRunningStatus`: `NOT_STARTED` / `IN_PROGRESS` / `COMPLETED`
- 오늘 러닝 미시작 시 프론트에서 "아직 오늘 러닝을 시작하지 않았어요 · 출발 전 스트레칭부터 시작해보세요" 문구 표시

---

## R3. 러닝 세션 (화면 3, 4)

### 3.1 러닝 준비 정보 조회

`GET /running-sessions/prepare?lat={lat}&lng={lng}` (화면 3)

**Response 200**

```json
{
  "locationLabel": "서울 성동구",
  "uvIndex": 6,
  "uvLevel": "보통",
  "goodTimeToRun": true,
  "stretching": {
    "title": "출발 전 스트레칭",
    "optional": true,
    "description": "발목·종아리 위주 3분 루틴"
  }
}
```

### 3.2 스트레칭 시작 (선택)

`POST /stretching-sessions`

**Request**

```json
{ "type": "PRE_RUN" }
```

**Response 201**

```json
{ "stretchingSessionId": "uuid", "startedAt": "2026-08-04T06:25:00+09:00" }
```

### 3.3 러닝 시작

`POST /running-sessions` (화면 3 → 4)

**Request**

```json
{
  "startedAt": "2026-08-04T06:30:00+09:00",
  "location": { "lat": 37.5665, "lng": 126.9780 },
  "uvIndexAtStart": 6
}
```

**Response 201**

```json
{ "runningSessionId": "uuid", "status": "IN_PROGRESS" }
```

### 3.4 러닝 진행 상태 폴링 (화면 4)

`GET /running-sessions/{id}/live`

- 클라이언트가 주기적으로 호출(또는 클라이언트 로컬 타이머 + 주기 동기화)

**Response 200**

```json
{
  "runningSessionId": "uuid",
  "elapsedSec": 1452,
  "intensity": "MODERATE",
  "distanceKm": 4.8,
  "heartRateStatus": "PENDING_AFTER_FINISH",
  "stressStatus": "PENDING_HRV_CALCULATION",
  "uvIndex": 6,
  "uvLevel": "보통"
}
```

- `heartRateStatus`: 러닝 중에는 항상 `PENDING_AFTER_FINISH` (화면 표기: "종료 후 확인")
- `stressStatus`: 항상 `PENDING_HRV_CALCULATION` (화면 표기: "심박변이도로 계산 예정")

### 3.5 러닝 종료

`POST /running-sessions/{id}/end`

**Request**

```json
{
  "endedAt": "2026-08-04T06:54:12+09:00",
  "durationSec": 1452,
  "distanceKm": 4.8,
  "intensity": "MODERATE",
  "routePath": [
    { "lat": 37.5440, "lng": 127.0557, "t": 0 },
    { "lat": 37.5442, "lng": 127.0559, "t": 8 }
  ]
}
```

- `routePath`: **선택.** 러닝 중 수집한 GPS 트랙을 종료 시점에 배열 통째로 한 번 보냅니다
  (러닝 중에는 보내지 않습니다). `t`는 러닝 시작부터의 경과 초입니다.
  - 프론트는 5~10초 간격 또는 일정 거리 이상 이동했을 때만 기록하세요(스로틀링).
  - 점 개수 상한 10,000개, `lat`/`lng`는 각각 ±90 / ±180 범위. 벗어나면 400 `E4001`
  - 생략하면 기존에 저장된 경로를 지우지 않습니다(멱등 재호출 대비).

> ⚠️ **저장은 되지만 아직 읽는 API가 없습니다.** 러닝 세션 조회 엔드포인트가 명세에 없어,
> History 화면이 경로를 그리려면 조회 API를 따로 만들어야 합니다.

**Response 200**

```json
{
  "runningSessionId": "uuid",
  "status": "ENDED",
  "nextStep": "HEART_RATE_CHECK",
  "defaultHeartRateSource": "WATCH"
}
```

- `defaultHeartRateSource`는 화면 5에서 기본으로 선택해 둘 측정 방식입니다.
  가장 최근 측정의 방식을 쓰고, 측정 이력이 없으면 애플 헬스 연동 여부에 따라 `WATCH`/`RPPG`입니다.
  사용자는 화면 5 상단의 버튼으로 다른 방식을 고를 수 있습니다.

---

## R4. 심박수 측정 (화면 5, 6)

### 4.1 측정 방식 선택

`POST /running-sessions/{id}/heart-rate/select-source` (화면 5)

**Request**

```json
{ "heartRateSource": "WATCH" }
```

**Response 200**

```json
{ "heartRateSource": "WATCH", "nextStep": "FETCH_APPLE_HEALTH" }
```

- `heartRateSource: "RPPG"` 선택 시 `nextStep: "RPPG_GUIDE"` 반환 → 화면 6으로 분기

### 4.2 워치 데이터 업로드 (애플 헬스 연동) (화면 5)

`POST /integrations/apple-health/heart-rate`

- HealthKit은 온디바이스 API라 서버가 직접 읽을 수 없습니다. 앱이 읽은 값을 업로드합니다.
- 앱은 HealthKit 읽기에 성공했을 때만 호출하므로 `syncStatus`는 항상 `SUCCESS`입니다.

**Request**

```json
{
  "runningSessionId": "uuid",
  "avgBpm": 152,
  "maxBpm": 168,
  "hrvMs": 42,
  "syncedAt": "2026-08-04T06:55:00"
}
```

- `hrvMs`는 선택입니다(기기·측정 조건에 따라 안 나올 수 있음).

**Response 201**

```json
{
  "heartRateMeasurementId": "uuid",
  "heartRateSource": "WATCH",
  "avgBpm": 152,
  "maxBpm": 168,
  "hrvMs": 42,
  "syncStatus": "SUCCESS"
}
```

> 서버가 애플 헬스를 호출하지 않게 되어 `E5010`은 이 엔드포인트에서 발생하지 않습니다.
> 공통 에러 코드 표(§0)에는 그대로 남아 있습니다.

### 4.3 애플 헬스 연동 기록 (최초 1회 / 워치 있음 선택 시)

`POST /integrations/apple-health/link`

- HealthKit 권한 동의는 OS 다이얼로그로 끝나므로 서버가 돌려줄 `authorizeUrl`이 없습니다.
- 앱이 동의 결과를 서버에 기록합니다. 사용자가 iOS 설정에서 권한을 회수하면 `false`로도 호출됩니다.

**Request**

```json
{ "linked": true }
```

**Response 200**

```json
{ "appleHealthLinked": true }
```

### 4.4 rPPG 측정 안내 조회 (화면 6)

`GET /heart-rate-measurements/rppg/guide`

**Response 200**

```json
{
  "instruction": "후면 카메라와 플래시에 손가락을 밀착시켜 약 12초간 측정해요",
  "durationSec": 12
}
```

### 4.5 rPPG 측정 시작

`POST /heart-rate-measurements/rppg/start`

**Request**

```json
{ "runningSessionId": "uuid" }
```

**Response 201**

```json
{ "rppgSessionId": "uuid", "status": "MEASURING", "durationSec": 12 }
```

### 4.6 rPPG 측정 결과 제출

`POST /heart-rate-measurements/rppg/{rppgSessionId}/result`

- 카메라 원본 영상은 서버로 전송하지 않고, 온디바이스 rPPG 알고리즘 처리 결과값만 업로드

**Request**

```json
{
  "avgBpm": 146,
  "maxBpm": 158,
  "hrvMs": 38,
  "measuredAt": "2026-08-04T07:42:00+09:00",
  "signalQuality": "GOOD"
}
```

**Response 201**

```json
{
  "heartRateMeasurementId": "uuid",
  "heartRateSource": "RPPG",
  "avgBpm": 146,
  "maxBpm": 158,
  "hrvMs": 38,
  "syncStatus": "SUCCESS"
}
```

- `signalQuality: "POOR"`인 경우 서버는 `syncStatus: "FAILED"`로 저장하고 **`avgBpm`/`maxBpm`/`hrvMs`를 `null`로 버립니다**
  (신뢰할 수 없는 값이 홈 대시보드의 주간 평균 bpm에 섞이지 않도록). 기록 화면의 "측정 실패 · 재측정 필요"에 대응하며,
  실패도 에러가 아니라 재측정이 필요한 기록이므로 응답은 201입니다.

---

## R5. AI 회복 가이드 (화면 7)

### 5.1 회복 가이드 생성

`POST /running-sessions/{id}/recovery-guide`

- 해당 세션의 운동 데이터(강도·거리) + 심박수 측정 결과 + UV 지수를 종합해 생성

**Response 201**

```json
{
  "recoveryGuideId": "uuid",
  "measuredBpm": 146,
  "summaryMessage": "오늘 강도 높은 4.8km 러닝에 UV 지수 6까지 겹쳤어요. 수분 보충과 가벼운 스트레칭으로 마무리하는 걸 추천해요.",
  "actions": [
    { "type": "HYDRATION", "title": "수분 보충", "description": "500ml 물 또는 이온음료로 회복을 도와요" },
    { "type": "COOLDOWN_STRETCH", "title": "쿨다운 스트레칭", "description": "종아리·햄스트링 위주 5분" }
  ],
  "cooldownTimerSec": 300
}
```

### 5.2 쿨다운 타이머 시작

`POST /recovery-guides/{recoveryGuideId}/cooldown-timer/start`

**Response 200**

```json
{ "cooldownTimerSec": 300, "startedAt": "2026-08-04T07:45:00+09:00" }
```

### 5.3 세션 완료 & 리포트 확정

`POST /running-sessions/{id}/complete` (화면 7 "완료하고 리포트 보기")

**Response 200**

```json
{
  "runningSessionId": "uuid",
  "status": "COMPLETED",
  "reportId": "uuid"
}
```

---

## R6. 측정 기록 (화면 8)

### 6.1 심박수 측정 기록 목록 조회

`GET /heart-rate-measurements?range=30d`

**Response 200**

```json
{
  "records": [
    {
      "heartRateMeasurementId": "uuid",
      "measuredAt": "2026-08-04T07:42:00+09:00",
      "heartRateSource": "RPPG",
      "avgBpm": 146,
      "runningSessionId": "sess_9f2a...",
      "syncStatus": "SUCCESS"
    },
    {
      "heartRateMeasurementId": "uuid",
      "measuredAt": "2026-08-03T06:58:00+09:00",
      "heartRateSource": "WATCH",
      "avgBpm": 152,
      "runningSessionId": "sess_7c11...",
      "syncStatus": "SUCCESS"
    },
    {
      "heartRateMeasurementId": "uuid",
      "measuredAt": "2026-08-01T07:10:00+09:00",
      "heartRateSource": "RPPG",
      "avgBpm": null,
      "runningSessionId": "sess_4b09...",
      "syncStatus": "FAILED"
    }
  ],
  "sourceRatio": {
    "watch": 1,
    "rppg": 2,
    "rppgFailedCount": 1
  }
}
```

- `range`는 `{일수}d` 형식입니다(`7d`, `30d`, `90d`…). 생략하면 `30d`. 형식이 어긋나거나 0 이하면 `E4001`입니다.
- `sourceRatio`는 같은 `range` 안의 실제 건수입니다. `rppgFailedCount`는 `rppg`에서 빠지는 값이 아니라 **부분집합**입니다.

### 6.2 실패 기록 재측정

`POST /heart-rate-measurements/{id}/retry` — 화면 8의 "전송 실패 · 재측정 필요" 액션. 4.4~4.6 rPPG 플로우로 리다이렉트

**Response 200**

```json
{ "retryFlow": "RPPG_GUIDE", "runningSessionId": "sess_4b09..." }
```

---

## R7. 프로필 & 설정 (화면 9)

### 7.1 프로필 조회

`GET /users/me/profile`

**Response 200**

```json
{
  "nickname": "김러너",
  "goal": { "goalType": "FITNESS", "weeklyRunGoal": 5 },
  "integrations": {
    "locationLinked": true,
    "cameraPermission": true,
    "locationPermission": true,
    "appleHealthLinked": true
  },
  "notifications": {
    "runningReminderTime": "07:00",
    "weeklyReportDay": "SUNDAY",
    "weeklyReportTime": "20:00"
  }
}
```

### 7.2 목표 수정

`PATCH /users/me/goal`

**Request** — 부분 수정. 보낸 필드만 변경됩니다.

```json
{ "goalType": "FITNESS", "weeklyRunGoal": 5 }
```

**Response 200**

```json
{ "goalType": "FITNESS", "weeklyRunGoal": 5, "updatedAt": "2026-08-04T22:10:00+09:00" }
```

**두 필드는 서로 다른 개념입니다.**

| 필드 | 역할 | 값 |
| --- | --- | --- |
| `goalType` | 운동 **목적** | `FITNESS`(체력 증진) \| `WEIGHT_LOSS`(체중 감량) \| `RACE_TRAINING`(완주 훈련) \| `STRESS_RELIEF`(스트레스 해소) |
| `weeklyRunGoal` | 주간 목표 **횟수** | 0 이상 정수. **km가 아니라 횟수입니다** |

- 후보에 없는 `goalType`(과거 예시값 `WEEKLY_DISTANCE` 포함)은 400 `E4001`입니다.
  `WEEKLY_DISTANCE`는 "목적"이 아니라 "목표 산정 기준"이라 다른 개념이었고, 후보에서 제거했습니다.
- 한글 라벨은 프론트가 매핑합니다.

> **주간 "거리" 목표(홈 화면의 `14.2 / 25.0km`)는 아직 저장할 곳이 없습니다.**
> `weeklyRunGoal`이 횟수 전용으로 확정됐기 때문입니다. `USER_GOALS`에 `weeklyDistanceGoalKm`를
> 신설할지, 거리 목표 UI를 없앨지 팀 결정이 필요합니다. 현재는 후자(미구현)로 두었습니다.

### 7.3 연동/권한 상태 조회

`GET /users/me/integrations`

**Response 200**

```json
{ "locationLinked": true, "cameraPermission": true, "locationPermission": true, "appleHealthLinked": true }
```

### 7.5 연동/권한 상태 갱신

`PATCH /users/me/integrations`

**Request** — 부분 수정. 보낸 필드만 변경됩니다.

```json
{ "cameraPermission": true, "locationPermission": false }
```

**Response 200** — 7.3과 같은 형태

```json
{ "locationLinked": false, "cameraPermission": true, "locationPermission": false, "appleHealthLinked": false }
```

- `appleHealthLinked`는 여기서 바꿀 수 없습니다 — §4.3 `POST /integrations/apple-health/link`가 담당합니다.
- 설정 행이 없던 사용자는 이 호출 시점에 생성됩니다.

> **서버 값은 "제어"가 아니라 "표시"용입니다.** 브라우저 권한(GPS·카메라)은 사용자가 앱 밖에서
> 언제든 바꿀 수 있어 서버 DB 값은 항상 참고용 캐시일 뿐, 권한 검증 수단이 될 수 없습니다.
> 기능 진입 시엔 항상 `navigator.geolocation`/`getUserMedia`를 다시 호출해 그 순간의 성공/실패로
> 판단하고, 그 결과를 이 API로 동기화해 프로필 화면에 "지금 상태"를 보여주는 용도로만 쓰세요.
>
> ```js
> // ❌ 서버 값만 믿고 기능 진입
> if (integrationStatus.cameraPermission) navigateToRppgScreen();
>
> // ✅ 매번 실제로 시도하고, 결과를 서버에도 동기화
> try {
>   await navigator.mediaDevices.getUserMedia({ video: true });
>   navigateToRppgScreen();
>   syncIntegrationStatus({ cameraPermission: true });
> } catch {
>   showPermissionDeniedGuide();
>   syncIntegrationStatus({ cameraPermission: false });
> }
> ```

### 7.4 알림 설정 변경

`PATCH /users/me/notifications`

**Request**

```json
{ "runningReminderTime": "07:00", "weeklyReportDay": "SUNDAY", "weeklyReportTime": "20:00" }
```

**Response 200**

```json
{ "runningReminderTime": "07:00", "weeklyReportDay": "SUNDAY", "weeklyReportTime": "20:00" }
```

---

## R8. 날씨

### 8.1 시간대별 UV 예보

`GET /weather/uv-forecast?lat={lat}&lng={lng}`

홈 화면의 UV 그래프용입니다. 러닝 세션과 무관하게 "이 위치의 오늘 하루 UV"를 반환합니다.

**Response 200**

```json
{
  "hourly": [
    { "hour": "00", "uv": 0 },
    { "hour": "02", "uv": 0 },
    { "hour": "04", "uv": 1 },
    { "hour": "22", "uv": 0 }
  ]
}
```

- `hourly`는 **항상 12개**입니다 — 00시부터 2시간 간격(`00, 02, … 22`). 길이 검사 없이 그대로 그래프에 넣으면 됩니다.
- **"지금 UV"와 "UV가 낮은 추천 시간대"는 이 배열 하나에서 계산하세요.** 별도 API를 두지 않았습니다.
- `lat`/`lng`가 없으면 400 `E4001`, 범위(±90 / ±180)를 벗어나도 400 `E4001`입니다.
- 인증이 필요합니다.

**에러**

| 상황 | 코드 |
| --- | --- |
| 좌표 누락/범위 초과 | 400 `E4001` |
| 기상청 API 실패(키 오류·장애·응답 없음) | 502 `E5011` |

**캐싱 (서버 구현 참고)**

- 캐시 키 `uv:forecast:{areaNo}:{yyyy-MM-dd}`, TTL 6시간, Redis.
- **키에 사용자 정보가 들어가지 않습니다.** 같은 광역시·도의 모든 사용자가 캐시 한 벌을 공유합니다.
- 하루치를 배열 하나로 저장합니다 — 기상청이 애초에 하루 전체를 한 번에 주므로 시간대별로 쪼개면 외부 호출만 늘어납니다.
- Redis가 죽어도 이 API는 동작합니다(캐시를 건너뛰고 매번 기상청에 조회). refresh token과 달리 정확성에 관여하지 않기 때문입니다.

> 📌 **기상청 자외선지수 API는 격자좌표(nx/ny)가 아니라 행정구역코드(`areaNo`)를 받습니다.**
> 격자좌표를 쓰는 건 초단기·단기예보이고 거기엔 UV 항목이 없습니다. 서버는 위경도를 광역시·도
> 17개 중 최근접 지점으로 매핑합니다. UV는 수십 km 단위로 거의 균일해 실용상 충분합니다.
>
> 기상청은 발표시각(하루 두 번) 기준 **3시간 간격**으로 주므로 서버가 2시간 격자로 선형 보간합니다.
> 발표 이전 구간은 0으로 채웁니다 — 비는 건 새벽뿐이고 새벽 UV는 실제로 0입니다.
>
> `KMA_AUTH_KEY`가 설정돼 있지 않으면 **모의값**으로 응답합니다(CI가 외부 API에 의존하지 않도록).
> 응답 형태는 동일하므로 프론트 연동에는 지장이 없습니다.

---

## 부록 A. 화면 ↔ 엔드포인트 매핑

| 화면 | 주요 엔드포인트 |
| --- | --- |
| 1. 로그인/회원가입 | 1.1, 1.2 |
| 2. 홈 | 2.1, 8.1 |
| 3. 러닝 준비 | 3.1, 3.2, 3.3 |
| 4. 러닝 진행 중 | 3.4, 3.5 |
| 5. 심박수 확인(워치) | 4.1, 4.2, 4.3 |
| 6. 심박수 확인(rPPG 안내) | 4.4, 4.5, 4.6 |
| 7. 오늘의 회복 가이드 | 5.1, 5.2, 5.3 |
| 8. 측정 기록 | 6.1, 6.2 |
| 9. 프로필 | 7.1 ~ 7.5, 1.4 |

## 부록 B. 참고 엔티티

| 엔티티 | 주요 필드 |
| --- | --- |
| User | userId, email, nickname, termsAgreedAt, privacyAgreedAt, marketingAgreedAt |
| UserGoal | goalType(운동 목적 enum), weeklyRunGoal(주간 횟수) |
| NotificationSetting | runningReminderTime, weeklyReportDay/Time |
| RunningSession | startedAt, endedAt, distanceKm, intensity, uvIndexAtStart, status, routePath |
| HeartRateMeasurement | heartRateSource(WATCH/RPPG), avgBpm, maxBpm, hrvMs, syncStatus, runningSessionId |
| RecoveryGuide | measuredBpm, summaryMessage, actions[], cooldownTimerSec |
| IntegrationStatus | locationLinked, cameraPermission, locationPermission, appleHealthLinked |