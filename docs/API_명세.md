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

• `GET /integrations/apple-health/heart-rate`

• `GET /integrations/apple-health/authorize` |
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
| E4010 | 401 | 인증 토큰 없음/만료 | 인증 토큰이 없거나 만료되었습니다. |
| E4030 | 403 | 권한 없음 (예: 카메라/위치 권한 미허용 상태에서 측정 요청) | 권한이 없습니다. |
| E4040 | 404 | 리소스 없음 (세션/측정 기록 없음) | 요청한 리소스를 찾을 수 없습니다. |
| E4090 | 409 | 이미 진행 중인 러닝 세션 존재 | 이미 진행 중인 러닝 세션이 있습니다. |
| E4091 | 409 | 이미 가입된 이메일 (회원가입) | 이미 사용 중인 이메일입니다. |
| E5000 | 500 | 서버 오류 | 서버 오류가 발생했습니다. |
| E5010 | 502 | 애플 헬스 연동 실패 | 애플 헬스 데이터를 가져오지 못했습니다. |

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
{ "email": "you@example.com", "password": "string", "nickname": "김러너" }
```

- `email`: 필수, 이메일 형식, 255자 이하
- `password`: 필수, 8자 이상 64자 이하 (BCrypt 해시로 저장, 응답에 절대 포함되지 않음)
- `nickname`: 필수, 100자 이하

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

### 1.4 로그아웃 (화면 9)

`POST /auth/logout`

**Response 204**

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
  "intensity": "MODERATE"
}
```

**Response 200**

```json
{
  "runningSessionId": "uuid",
  "status": "ENDED",
  "nextStep": "HEART_RATE_CHECK"
}
```

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

### 4.2 워치 데이터 조회 (애플 헬스 연동) (화면 5)

`GET /integrations/apple-health/heart-rate?runningSessionId={id}`

**Response 200**

```json
{
  "heartRateSource": "WATCH",
  "avgBpm": 152,
  "maxBpm": 168,
  "hrvMs": 42,
  "syncedAt": "2026-08-04T06:55:00+09:00"
}
```

**연동 실패 시 (E5010)** — 실패 응답은 래퍼 전체를 그대로 표기했습니다.

```json
{
  "success": false,
  "data": null,
  "error": { "code": "E5010", "message": "애플 헬스 데이터를 가져오지 못했습니다." }
}
```

### 4.3 애플 헬스 연동 시작 (최초 1회 / 워치 있음 선택 시)

`GET /integrations/apple-health/authorize`

**Response 200**

```json
{ "authorizeUrl": "healthkit://authorize?..." }
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

- `signalQuality: "POOR"`인 경우 서버는 `syncStatus: "FAILED"`로 저장하고 재측정 유도 (기록 화면의 "측정 실패 · 재측정 필요"에 대응)

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
    "watch": 2,
    "rppg": 2,
    "rppgFailedCount": 1
  }
}
```

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
  "goal": { "goalType": "체력 증진", "weeklyRunGoal": 5 },
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

**Request**

```json
{ "goalType": "체력 증진", "weeklyRunGoal": 5 }
```

**Response 200**

```json
{ "goalType": "체력 증진", "weeklyRunGoal": 5, "updatedAt": "2026-08-04T22:10:00+09:00" }
```

### 7.3 연동/권한 상태 조회

`GET /users/me/integrations`

**Response 200**

```json
{ "locationLinked": true, "cameraPermission": true, "locationPermission": true, "appleHealthLinked": true }
```

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

## 부록 A. 화면 ↔ 엔드포인트 매핑

| 화면 | 주요 엔드포인트 |
| --- | --- |
| 1. 로그인/회원가입 | 1.1, 1.2 |
| 2. 홈 | 2.1 |
| 3. 러닝 준비 | 3.1, 3.2, 3.3 |
| 4. 러닝 진행 중 | 3.4, 3.5 |
| 5. 심박수 확인(워치) | 4.1, 4.2, 4.3 |
| 6. 심박수 확인(rPPG 안내) | 4.4, 4.5, 4.6 |
| 7. 오늘의 회복 가이드 | 5.1, 5.2, 5.3 |
| 8. 측정 기록 | 6.1, 6.2 |
| 9. 프로필 | 7.1 ~ 7.4, 1.4 |

## 부록 B. 참고 엔티티

| 엔티티 | 주요 필드 |
| --- | --- |
| User | userId, email, nickname |
| UserGoal | goalType, weeklyRunGoal |
| NotificationSetting | runningReminderTime, weeklyReportDay/Time |
| RunningSession | startedAt, endedAt, distanceKm, intensity, uvIndexAtStart, status |
| HeartRateMeasurement | heartRateSource(WATCH/RPPG), avgBpm, maxBpm, hrvMs, syncStatus, runningSessionId |
| RecoveryGuide | measuredBpm, summaryMessage, actions[], cooldownTimerSec |
| IntegrationStatus | locationLinked, cameraPermission, locationPermission, appleHealthLinked |