# R4 심박수 측정 · R6 측정 기록 API 설계

- 작성일: 2026-08-10
- 브랜치: `feature/heartrate-api` (PR 대상 `main`)
- 대상: `docs/API_명세.md` R4 (화면 5, 6), R6 (화면 8)
- 범위 밖: R5 AI 회복 가이드, R7 프로필

## 1. 배경

`heartrate/` 패키지에는 `HeartRateMeasurement` 엔티티와 리포지토리만 있고 API가 없다. R2 홈 대시보드가 집계용으로만 읽고 있다. 이번 작업에서 측정 흐름(R4)과 기록 조회(R6)를 구현한다.

DB는 이미 준비되어 있다. V7이 `heart_rate_measurements`를, V4가 `integration_status`를 정의한다. **새 마이그레이션은 만들지 않는다.**

## 2. 명세 대비 변경점

명세 그대로 구현할 수 없거나, 명세에 없는 요구가 추가된 지점이다.

### 2.1 애플 헬스: GET → POST

명세 4.2는 `GET /integrations/apple-health/heart-rate`로 **서버가** 애플 헬스 데이터를 가져오는 형태다. HealthKit은 온디바이스 API라 서버가 직접 읽을 수 없다.

앱이 HealthKit에서 읽은 값을 서버로 올리는 구조로 바꾼다.

- `GET /integrations/apple-health/heart-rate?runningSessionId={id}` → **`POST /integrations/apple-health/heart-rate`**

### 2.2 애플 헬스 authorize → link

명세 4.3 `GET /integrations/apple-health/authorize`가 반환하는 `authorizeUrl`도 같은 이유로 성립하지 않는다. HealthKit 권한 동의는 OS 다이얼로그로 끝난다.

앱이 동의 결과를 서버에 기록하는 엔드포인트로 대체한다. `integration_status.apple_health_linked`(V4에 이미 존재)를 갱신하고, R7 프로필이 같은 컬럼을 재사용한다.

- `GET /integrations/apple-health/authorize` → **`POST /integrations/apple-health/link`**

### 2.3 E5010은 R4에서 쓰이지 않는다

`APPLE_HEALTH_SYNC_FAILED`(E5010)는 서버가 애플 헬스를 호출하다 실패하는 경우였다. 2.1의 POST 전환으로 그 경로가 사라졌다.

`ErrorCode` enum에서 **삭제하지 않는다.** `ErrorCodeTest`가 명세 §0 공통 에러 코드 표와의 1:1 대응을 고정하고 있다. 사용처만 비운다.

### 2.4 신규 요구: 최근 선택한 측정 방식이 기본값

명세에 없는 요구다. 화면 5 상단에 WATCH/RPPG 두 선택지가 나오고, **최근에 쓴 방식이 기본으로 선택된 상태**로 뜬다. 사용자가 다른 쪽 버튼을 누르면 그 방식으로 전환할 수 있다.

명세의 `select-source`는 선택을 받기만 할 뿐, 화면 진입 시 기본값을 내려주는 통로가 없다. **새 엔드포인트를 만들지 않고 3.5 `POST /running-sessions/{id}/end` 응답에 `defaultHeartRateSource`를 추가한다.** 러닝 종료 → 화면 5 진입이 유일한 경로이므로 이 응답이 정확한 시점이다.

값은 저장하지 않고 파생한다:

1. `findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(userId)` — R2 홈이 이미 쓰는 쿼리를 재사용 — 로 최근 측정 1건을 읽어 그 `heartRateSource`를 쓴다.
2. 측정 이력이 없으면 `integration_status.apple_health_linked`가 `true`일 때 `WATCH`, 아니면 `RPPG`.
3. `integration_status` 행 자체가 없으면 `RPPG`.

새 컬럼도 새 마이그레이션도 필요 없다. 다만 **고르기만 하고 측정을 끝내지 않은 선택은 기억되지 않는다** — 측정 이력에서 파생하므로. 의도된 절충이다.

### 2.5 sourceRatio 정의

명세 6.1 예시는 `records`가 3건인데 `sourceRatio`가 `watch:2 + rppg:2 = 4`로 서로 맞지 않는다. 예시가 잘린 것으로 보고, **같은 `range` 안의 실제 건수**로 정의한다.

- `watch` — `heartRateSource == WATCH`인 건수
- `rppg` — `heartRateSource == RPPG`인 건수 (성공·실패 모두 포함)
- `rppgFailedCount` — `heartRateSource == RPPG && syncStatus == FAILED`인 건수

`rppgFailedCount`는 `rppg`의 부분집합이다.

## 3. 엔드포인트

R4·R6의 신규 엔드포인트 8개와 기존 3.5 수정 1개다. 모두 인증이 필요하다. `SecurityConfig.PUBLIC_PATHS`와 `OpenApiConfig`는 손대지 않는다 — 전역 보안이 이미 걸려 있다.

| # | 메서드·경로 | 명세 대비 |
|---|---|---|
| 3.5 | `POST /running-sessions/{id}/end` | 응답에 `defaultHeartRateSource` 추가 (§2.4) |
| 4.1 | `POST /running-sessions/{id}/heart-rate/select-source` | 그대로 |
| 4.2 | `POST /integrations/apple-health/heart-rate` | GET → POST (§2.1) |
| 4.3 | `POST /integrations/apple-health/link` | authorize 대체 (§2.2) |
| 4.4 | `GET /heart-rate-measurements/rppg/guide` | 그대로 |
| 4.5 | `POST /heart-rate-measurements/rppg/start` | 그대로 |
| 4.6 | `POST /heart-rate-measurements/rppg/{rppgSessionId}/result` | 그대로 |
| 6.1 | `GET /heart-rate-measurements?range=30d` | 그대로 |
| 6.2 | `POST /heart-rate-measurements/{id}/retry` | 그대로 |

### 3.5 러닝 종료 (기존 수정)

`RunningEndDto.Response`에 필드 하나 추가.

```json
{
  "runningSessionId": "uuid",
  "status": "ENDED",
  "nextStep": "HEART_RATE_CHECK",
  "defaultHeartRateSource": "RPPG"
}
```

멱등 동작은 그대로 유지한다. 이미 끝난 세션에 다시 호출해도 에러 없이 현재 상태를 반환한다.

### 4.1 측정 방식 선택

```
POST /running-sessions/{id}/heart-rate/select-source
req  { "heartRateSource": "WATCH" }
res  200  { "heartRateSource": "WATCH", "nextStep": "FETCH_APPLE_HEALTH" }
```

- `WATCH` → `nextStep: "FETCH_APPLE_HEALTH"`
- `RPPG` → `nextStep: "RPPG_GUIDE"`

**선택값을 저장하지 않는다.** 이후 흐름(4.2/4.6)이 각자 source를 확정하므로 읽는 곳이 없다. 세션 소유자 검증만 수행하고 분기 힌트를 반환한다.

경로가 `/running-sessions`로 시작하므로 **기존 `RunningSessionController`에 추가한다** (새 컨트롤러 없음). 로직은 `HeartRateMeasurementService`에 둔다.

### 4.2 워치 데이터 업로드

```
POST /integrations/apple-health/heart-rate
req  { "runningSessionId": "uuid", "avgBpm": 152, "maxBpm": 168,
       "hrvMs": 42, "syncedAt": "2026-08-04T06:55:00" }
res  201 { "heartRateMeasurementId": "uuid", "heartRateSource": "WATCH",
           "avgBpm": 152, "maxBpm": 168, "hrvMs": 42, "syncStatus": "SUCCESS" }
```

`syncedAt`을 `measured_at`으로 저장한다. `heart_rate_source = WATCH`, `sync_status = SUCCESS`, `signal_quality = null`.

앱이 HealthKit 읽기에 성공했을 때만 호출하므로 WATCH 행은 항상 `SUCCESS`다.

### 4.3 애플 헬스 연동 기록

```
POST /integrations/apple-health/link
req  { "linked": true }
res  200 { "appleHealthLinked": true }
```

`integration_status` 행이 없으면 생성하고, 있으면 갱신한다.

### 4.4 rPPG 안내

```
GET /heart-rate-measurements/rppg/guide
res  200 { "instruction": "후면 카메라와 플래시에 손가락을 밀착시켜 약 12초간 측정해요",
           "durationSec": 12 }
```

고정 상수. DB 조회 없음.

### 4.5 rPPG 측정 시작

```
POST /heart-rate-measurements/rppg/start
req  { "runningSessionId": "uuid" }
res  201 { "rppgSessionId": "uuid", "status": "MEASURING", "durationSec": 12 }
```

세션 소유자를 검증하고, 새 `rppgSessionId`(UUID)를 발급해 Redis에 매핑을 저장한다 (§4.2). `durationSec`은 4.4와 같은 상수를 공유한다.

**DB에는 아무것도 쓰지 않는다.**

### 4.6 rPPG 결과 제출

```
POST /heart-rate-measurements/rppg/{rppgSessionId}/result
req  { "avgBpm": 146, "maxBpm": 158, "hrvMs": 38,
       "measuredAt": "2026-08-04T07:42:00", "signalQuality": "GOOD" }
res  201 { "heartRateMeasurementId": "uuid", "heartRateSource": "RPPG",
           "avgBpm": 146, "maxBpm": 158, "hrvMs": 38, "syncStatus": "SUCCESS" }
```

Redis에서 `rppgSessionId` → `runningSessionId`를 꺼낸다. 키가 없으면(TTL 만료 또는 위조) `E4040`.

여기서 `heart_rate_measurements` 행이 **처음** 생성된다.

`signalQuality`에 따라 갈린다:

| signalQuality | syncStatus | avgBpm / maxBpm / hrvMs |
|---|---|---|
| `GOOD` | `SUCCESS` | 요청값 그대로 |
| `POOR` | `FAILED` | **`null`로 저장** |

POOR일 때 값을 버리는 이유는 §4.3에 있다.

성공·실패 모두 201이다. 실패는 에러가 아니라 "재측정이 필요한 기록"이며, 화면 8의 "측정 실패 · 재측정 필요" 항목에 대응한다.

Redis 키는 결과 제출 후 삭제한다 (같은 `rppgSessionId`로 두 번 제출 불가).

### 6.1 측정 기록 목록

```
GET /heart-rate-measurements?range=30d
res  200 { "records": [ { "heartRateMeasurementId", "measuredAt", "heartRateSource",
                          "avgBpm", "runningSessionId", "syncStatus" }, ... ],
           "sourceRatio": { "watch": 2, "rppg": 2, "rppgFailedCount": 1 } }
```

로그인한 사용자의 기록만, `measuredAt` 내림차순.

`range`는 `{n}d` 형식만 파싱한다 (`7d`, `30d`, `90d` 등). 생략하면 `30d`. 형식이 어긋나거나 `n`이 양수가 아니면 `E4001`. 조회 하한은 `현재 시각 - n일` 이상(`>=`)이다.

`sourceRatio`는 **조회된 목록을 자바에서 세서** 만든다. 30일치면 많아야 수십 건이라 별도 집계 쿼리를 두지 않는다.

### 6.2 실패 기록 재측정

```
POST /heart-rate-measurements/{id}/retry
res  200 { "retryFlow": "RPPG_GUIDE", "runningSessionId": "uuid" }
```

측정 기록 → 러닝 세션 → user를 타고 소유자를 검증하고, 그 `runningSessionId`를 반환한다. 앱은 이걸 들고 4.4~4.6 흐름을 다시 탄다.

**실패 기록을 삭제하지 않는다.** `RUNNING_SESSIONS : HEART_RATE_MEASUREMENTS = 1:N`이므로 재측정 성공 행이 추가로 쌓이고, 실패 이력은 그대로 남는다. ERD가 1:N인 이유가 이것이다.

`syncStatus`가 `FAILED`가 아닌 기록에 호출해도 막지 않는다 — 명세에 전용 에러 코드가 없고, 사용자가 멀쩡한 측정을 다시 하겠다는 것을 거부할 이유가 없다.

## 4. 데이터 설계

### 4.1 enum 확정

CLAUDE.md의 "enum 후보 컬럼은 String으로 두고, 실제로 쓰는 도메인에서 enum을 확정"에 따라 R4에서 확정한다. V7 컬럼이 이미 `VARCHAR`이므로 **DDL 변경이 없다.**

```java
public enum SyncStatus { SUCCESS, FAILED }
public enum SignalQuality { GOOD, POOR }
```

`HeartRateMeasurement`의 `String syncStatus` / `String signalQuality`를 이 타입으로 바꾸고 `@Enumerated(EnumType.STRING)`을 붙인다.

명세가 언급한 값이 정확히 이 넷이다. `PENDING`은 만들지 않는다 — 측정 중 상태를 Redis가 들고 있어 DB에 미완성 행이 생기지 않는다 (§4.2).

### 4.2 rPPG 세션은 Redis

4.6 요청 본문에 `runningSessionId`가 없다. 서버가 `rppgSessionId` → `runningSessionId` 매핑을 4.5와 4.6 사이에 들고 있어야 한다.

`RppgSessionStore`가 `RefreshTokenStore`와 같은 패턴으로 `StringRedisTemplate`을 쓴다.

- 키: `rppg:{rppgSessionId}`
- 값: `runningSessionId`
- TTL: 10분 (측정 자체는 12초)

Postgres에 PENDING 행을 미리 만드는 대안을 쓰지 않은 이유: 사용자가 측정 도중 앱을 끄면 빈 행이 영구히 남고, 6.1 목록과 `sourceRatio` 집계에서 매번 걸러내야 한다. Redis는 TTL로 알아서 사라진다. CLAUDE.md의 저장소 역할 분담("사라져도 다시 만들 수 있는 것 = Redis")과도 일치한다.

### 4.3 POOR 측정값은 null로 저장

`HeartRateMeasurementRepository.avgBpmBetween`은 R2 홈 대시보드의 주간 평균 bpm을 구하는데, **`sync_status`를 거르지 않는다.** POOR 측정의 bpm을 그대로 저장하면 신뢰할 수 없는 값이 홈 대시보드 평균을 오염시킨다.

null로 저장하면 `avg()`가 null을 무시하므로 **기존 쿼리를 손대지 않아도 된다.** 명세 6.1의 실패 기록 예시(`avgBpm: null`)와도 일치한다.

### 4.4 새 마이그레이션 없음

| 필요한 것 | 어디에 이미 있는가 |
|---|---|
| 측정 기록 저장 | V7 `heart_rate_measurements` |
| 애플 헬스 연동 여부 | V4 `integration_status.apple_health_linked` |
| enum 값 | V7 컬럼이 이미 `VARCHAR` |
| 최근 선택 방식 | 저장하지 않고 파생 (§2.4) |
| rPPG 세션 | Redis (§4.2) |

## 5. 패키지 구조

```
heartrate/
├── controller/
│   ├── HeartRateMeasurementController   @RequestMapping("/heart-rate-measurements")
│   │                                    4.4 · 4.5 · 4.6 · 6.1 · 6.2
│   └── AppleHealthController            @RequestMapping("/integrations/apple-health")
│                                        4.2 · 4.3
├── service/
│   └── HeartRateMeasurementService
├── entity/
│   ├── HeartRateMeasurement             (수정: String 2개 → enum, 정적 팩토리 추가)
│   ├── HeartRateSource                  (기존)
│   ├── SyncStatus                       (신규)
│   └── SignalQuality                    (신규)
├── repository/
│   ├── HeartRateMeasurementRepository   (조회 쿼리 추가)
│   └── RppgSessionStore                 (신규, Redis)
└── dto/
    ├── SelectSourceDto                  4.1
    ├── AppleHealthDto                   4.2 · 4.3
    ├── RppgGuideResponse                4.4
    ├── RppgStartDto                     4.5
    ├── RppgResultDto                    4.6
    ├── HeartRateRecordsResponse         6.1
    └── RetryResponse                    6.2

profile/
├── entity/IntegrationStatus             (신규, V4 전체 매핑)
└── repository/IntegrationStatusRepository

running/  (수정)
├── dto/RunningEndDto                    Response에 defaultHeartRateSource 추가
├── service/RunningSessionService        기본 source 파생
└── controller/RunningSessionController  4.1 추가
```

새 컨트롤러는 2개뿐이다. 4.1은 경로가 `/running-sessions/...`로 시작해 기존 `RunningSessionController`의 클래스 매핑에 그대로 들어맞는다.

도메인 간 의존이 세 군데 생긴다. `HomeService`가 이미 여러 도메인을 집계하는 전례가 있어 같은 방향으로 간다.

- `RunningSessionController` → `HeartRateMeasurementService` (4.1 위임)
- `RunningSessionService` → `HeartRateMeasurementRepository` + `IntegrationStatusRepository` (§2.4 기본 source 파생, 두 리포지토리가 모두 필요)
- `HeartRateMeasurementService` → `RunningSessionRepository` (세션 소유자 검증)

소유자 검증 헬퍼(`getOwnedSession`)는 `RunningSessionService`와 `HeartRateMeasurementService`에 각각 둔다. private 헬퍼 3줄을 공유하려고 도메인 간 서비스 의존을 하나 더 만들지 않는다.

### 5.1 엔티티 규약

`User`/`RunningSession` 패턴을 따른다. `@Getter @Builder @NoArgsConstructor(PROTECTED) @AllArgsConstructor(PRIVATE)`, setter 없음.

`IntegrationStatus`는 `UserGoal`처럼 1:1 테이블이므로 `@GeneratedValue` 없이 `user_id`를 PK로 쓴다. V4의 네 컬럼(`location_linked`, `camera_permission`, `location_permission`, `apple_health_linked`)을 **전부** 매핑한다 — 지금 쓰는 건 하나뿐이지만 R7이 같은 테이블을 다시 연다.

상태 전이는 엔티티 메서드로:

- `HeartRateMeasurement.watch(session, avgBpm, maxBpm, hrvMs, measuredAt)` — 정적 팩토리
- `HeartRateMeasurement.rppg(session, avgBpm, maxBpm, hrvMs, measuredAt, signalQuality)` — 정적 팩토리, POOR이면 내부에서 bpm/hrv를 null로 (§4.3)
- `IntegrationStatus.linkAppleHealth(boolean)`

### 5.2 리포지토리 쿼리 추가

```java
// 6.1 목록 — 로그인 사용자의 range 내 측정 기록, 최신순
List<HeartRateMeasurement> findByRunningSession_User_UserIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDesc(
        UUID userId, LocalDateTime since);
```

기본 source 파생(§2.4)은 기존 `findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc`를 그대로 쓴다.

## 6. 에러 처리

**새 에러 코드를 만들지 않는다.** 기존 `ErrorCode`로 전부 커버된다.

| 상황 | 코드 |
|---|---|
| 잘못된 `range` 형식, 요청 본문 검증 실패 | `E4001` `INVALID_REQUEST` |
| 토큰 없음/만료 | `E4010` `UNAUTHORIZED` |
| 남의 러닝 세션 / 남의 측정 기록 | `E4030` `FORBIDDEN` |
| 없는 세션·측정 기록, 만료된 `rppgSessionId` | `E4040` `NOT_FOUND` |

소유자 검증은 `RunningSessionService.getOwnedSession()`과 같은 방식으로 `HeartRateMeasurementService`의 private 헬퍼로 뺀다. 남의 리소스는 404가 아니라 `FORBIDDEN`이다 (CLAUDE.md 관례).

만료된 `rppgSessionId`가 `E4040`인 것은 소유권 판단 자체가 불가능하기 때문이다 — 어느 세션의 것인지 알 수 없다.

서비스가 예외를 던질 때 `throw new BusinessException(ErrorCode.X); // E40xx` 형태로 주석에 코드 번호를 남긴다.

## 7. 테스트

`HeartRateMeasurementServiceTest` 한 파일. 값이 틀리면 홈 대시보드까지 오염되는 지점만 덮는다.

1. `range` 파싱 — `30d` 정상, 생략 시 기본 30일, `abc`/`0d`/`-5d` → `E4001`
2. `sourceRatio` 집계 — WATCH/RPPG 섞인 목록에서 `watch`/`rppg`/`rppgFailedCount`가 맞는지, `rppgFailedCount ⊆ rppg`인지
3. POOR → `syncStatus=FAILED` + bpm/hrv `null` (§4.3)
4. 기본 source 파생 — 이력 있음 / 이력 없고 `appleHealthLinked=true` / 이력 없고 `false` / `integration_status` 행 없음 (§2.4)

**Redis를 쓰는 테스트는 `@Transactional`로 롤백되지 않는다.** rPPG 흐름을 테스트하면 키를 직접 지워야 한다 (CLAUDE.md).

`docker compose up -d`가 선행되어야 한다. `@SpringBootTest`가 실제 PostgreSQL에 붙는다.

## 8. 구현 순서

1. enum 2개 (`SyncStatus`, `SignalQuality`) + `HeartRateMeasurement` 수정 (정적 팩토리 포함)
2. `IntegrationStatus` 엔티티 + 리포지토리
3. `RppgSessionStore` (Redis)
4. `HeartRateMeasurementRepository` 쿼리 추가
5. `HeartRateMeasurementService` — 4.1~4.6, 6.1, 6.2
6. 컨트롤러 2개 + `RunningSessionController` 4.1 추가
7. `RunningEndDto.Response` + `RunningSessionService` 기본 source 파생
8. `HeartRateMeasurementServiceTest`
9. `docs/API_명세.md`에 §2 변경점 반영

## 9. 검증

```bash
docker compose ps                       # postgres/redis healthy 확인이 먼저
./gradlew test
./gradlew build
```

CI 환경(`test` 프로파일) 재현은 CLAUDE.md의 명령을 쓴다. 설정 키를 추가하지 않으므로 `application-local.yml`/`application-test.yml`은 손대지 않는다.
