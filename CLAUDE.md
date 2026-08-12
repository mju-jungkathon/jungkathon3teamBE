# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

AfterGrow — 러닝 트래킹 + 심박수 측정 + AI 회복 가이드 백엔드 (팀 `jungkathon3team`, org `mju-jungkathon`).

**패키지명은 `aftergrow`입니다. `afterglow`가 아닙니다.** 과거에 로깅 설정 등에서 반복적으로 오타가 났던 부분이니 새 파일 작성 시 주의하세요. 이전 프로젝트명(`aura`/`aac-aura`) 잔재는 현재 전부 정리된 상태이니, 다시 등장하면 `aftergrow`로 고칩니다.

## 명령어

```bash
docker compose up -d          # PostgreSQL + Redis (실행 필수, 아래 참고)
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test
./gradlew test --tests '*AftergrowApplicationTests'          # 단일 테스트 클래스
./gradlew test --tests '*AftergrowApplicationTests.contextLoads'  # 단일 메서드
./gradlew build
```

**`test`/`build`는 Docker 컨테이너가 떠 있어야 통과합니다.** `contextLoads()`가 `@SpringBootTest`로 실제 PostgreSQL에 붙기 때문에 컨테이너 없이 돌리면 HibernateException으로 실패합니다. 빌드 실패 시 가장 먼저 `docker compose ps`로 postgres/redis가 healthy인지 확인하세요.

테스트는 9개 클래스 51개 메서드이고 **전부 `auth`/`common`만 덮습니다** — running·home·heartrate 도메인에는 테스트가 하나도 없습니다. 그쪽을 고칠 때 초록불은 아무것도 보장하지 않으니 직접 확인하세요. **로컬은 `local` 프로파일, CI는 `test` 프로파일**로 돌아갑니다.

CI 환경을 로컬에서 재현하려면 (프로파일별로 설정이 달라 한쪽만 통과하는 일이 생깁니다):

```bash
SPRING_PROFILES_ACTIVE=test SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/aftergrow_test SPRING_DATASOURCE_USERNAME=dev SPRING_DATASOURCE_PASSWORD=... SPRING_DATA_REDIS_HOST=localhost ./gradlew cleanTest test
```

- 서버: http://localhost:8080 / Swagger UI: http://localhost:8080/swagger-ui.html
- Swagger에서 인증이 필요한 API를 호출하려면 우측 상단 **Authorize**에 로그인 응답의 `accessToken`을 넣으세요(refreshToken 아님). 설정은 `common/config/OpenApiConfig`에 있고 **전역으로 걸려 있어**, 토큰 없이 호출하는 엔드포인트에는 `@SecurityRequirements`(복수형, 빈 값)를 붙여 해제합니다.
- 공개 경로는 `SecurityConfig`의 `PUBLIC_PATHS`에 **명시적으로 나열**돼 있습니다(`/auth/signup`, `/auth/login`, `/auth/refresh`, swagger). 그 외는 `Authorization: Bearer {accessToken}`이 필요합니다. **`/auth/**` 와일드카드를 쓰지 마세요** — `/auth/logout`은 인증이 필요한데 와일드카드로 열면 `@AuthenticationPrincipal`이 null로 들어옵니다.
- lint/formatter 설정 없음(`.editorconfig`, checkstyle, spotless 모두 없음).

## 설정 구조

`application.yml`에는 앱 이름과 활성 프로파일(`local`)만 있고, **실제 datasource/redis/jwt 설정은 전부 `application-local.yml`에 있습니다. 이 파일은 `.env`와 함께 gitignore 대상입니다** — 클론 직후에는 존재하지 않으므로 직접 만들어야 하고, 값은 `.env`(원본은 `.env.example`)와 일치시켜야 합니다. 설정 키를 추가할 때는 `.env.example`에도 항목 이름을 추가해 두세요.

CI(`.github/workflows/test.yml`)는 `SPRING_PROFILES_ACTIVE=test`로 돌고, **datasource/redis 접속 정보만 환경변수로 주입**합니다. 나머지(`jwt.*`, `ddl-auto`, flyway)는 커밋되는 `src/test/resources/application-test.yml`에 있습니다.

> ⚠️ **설정 키를 추가할 때는 세 파일 전부에 넣으세요** — `application-local.yml`(로컬), `src/test/resources/application-test.yml`(CI), `src/main/resources/application-prod.yml`(배포). local에만 넣으면 로컬 테스트는 통과하고 CI만 `PlaceholderResolutionException`으로 죽습니다. 실제로 `jwt.*`에서 한 번 겪었습니다. **prod만 빠뜨리면 로컬과 CI가 전부 통과하고 배포된 서버만 기동 시 죽습니다** — 가장 늦게 발견되는 형태입니다.

`gradlew`는 git에 `100755`로 기록돼 있어야 합니다. Windows는 `core.fileMode=false`라 권한 비트를 무시하므로, 실수로 `100644`가 되면 로컬에선 멀쩡하고 Ubuntu 러너에서만 `Permission denied`(exit 126)로 죽습니다. `git ls-files -s gradlew`로 확인하고 `git update-index --chmod=+x gradlew`로 고칩니다.

## 배포

**`main`에 머지하면 배포까지 자동으로 끝납니다.** `.github/workflows/deploy.yml`이 이미지를 빌드해 `ghcr.io/mju-jungkathon/aftergrow`에 `:latest`·`:{sha}`로 올린 뒤, SSH로 EC2에 들어가 `git pull` → `compose pull` → `up -d` → 헬스체크까지 수행합니다. 헬스체크가 90초 안에 200을 주지 못하면 워크플로가 실패합니다.

**EC2(t2.micro, 1GB)에서 `docker build`나 `./gradlew build`를 돌리지 마세요.** postgres·redis·app이 같은 호스트에 있어서, Gradle이 메모리를 다 쓰면 돌아가던 컨테이너가 OOM killer에 종료됩니다. EC2가 하는 일은 pull 뿐입니다.

**배포 때마다 수십 초 끊깁니다** — `up -d`가 컨테이너를 내렸다 새로 띄웁니다. 시연 중에는 `main`에 머지하지 마세요.

수동으로 배포해야 할 때(워크플로가 막혔을 때)만 EC2에서:

```bash
cd /home/ubuntu/jungkathon3teamBE
git pull origin main
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
curl localhost:8080/actuator/health     # {"status":"UP"}
```

- **EC2에서 파일을 직접 고치지 마세요.** 고쳐도 다음 배포의 `git pull`에서 막히거나 덮어써집니다. 문제 3이 그렇게 생겼습니다.
- `docker-compose.yml`은 **로컬 개발용**(postgres·redis만), `docker-compose.prod.yml`이 **배포용**(app 포함)입니다. override 체인은 쓰지 않습니다.
- 리포지토리 시크릿 `EC2_HOST`·`EC2_USER`·`EC2_SSH_KEY`가 배포에 쓰입니다. EC2를 재생성하면 셋 다 갱신해야 합니다.
- prod compose는 postgres·redis 포트를 공개하지 않습니다. app의 8080만 열립니다.
- GHCR 패키지는 private이라 EC2에서 최초 1회 `docker login ghcr.io`(PAT, `read:packages`)가 필요합니다.
- **DB 볼륨을 지우지 않도록 `docker compose down -v`를 쓰지 마세요.**
- `/actuator/health`는 `SecurityConfig`의 `PUBLIC_PATHS`에 있어 토큰 없이 열립니다. actuator 기본 노출은 `health` 하나뿐이고 `show-details`는 `never`라 응답은 `{"status":"UP"}`뿐입니다. **Redis가 죽으면 503이 되는데 이는 의도된 동작입니다** — refresh token이 Redis에만 있어 로그인·재발급이 실제로 불가능한 상태이기 때문입니다.

## 아키텍처

### 스택 선택의 제약 (바꾸지 말 것)

- **Java 17 고정.** Java 21에서 springdoc-openapi와 Jackson 호환성 문제를 겪고 되돌린 결정입니다. `build.gradle` toolchain과 CI 워크플로 둘 다 17이어야 합니다.
- **Spring Boot 4.0.7.** starter 이름이 Boot 3과 다릅니다 — `spring-boot-starter-web`이 아니라 `spring-boot-starter-webmvc`, 테스트도 `spring-boot-starter-*-test`로 스타터마다 쪼개져 있습니다. 의존성 추가 시 Boot 4 기준 이름을 확인하세요. springdoc은 Boot 4 호환인 3.x(`3.0.2`)를 씁니다.
- Flyway는 `flyway-core` + `flyway-database-postgresql` + `spring-boot-starter-flyway`(Boot 4 자동설정용) 셋 다 필요합니다.

#### Boot 4에서 패키지가 바뀐 것 (실제로 걸렸던 것들)

Boot 3 기준 코드나 예제를 그대로 붙여넣으면 컴파일이 깨집니다. import를 추측하지 말고 확인하세요.

| 대상 | Boot 3 (틀림) | Boot 4 (맞음) |
|---|---|---|
| Jackson | `com.fasterxml.jackson.databind.ObjectMapper` | **`tools.jackson.databind.ObjectMapper`** (Jackson 3.1.4). Jackson 2도 classpath에 있지만 Boot가 빈으로 등록하는 건 3입니다 — 2로 주입하면 `NoSuchBeanDefinitionException` |
| MockMvc | `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | **`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`** |

Spring Security는 **7.0.6**, Spring Framework는 7.0.8입니다. 람다 DSL만 사용 가능합니다(`and()` 없음).

### 저장소 역할 분담

- **PostgreSQL** — 서버가 재시작돼도 남아야 하는 것: 사용자, 러닝 세션 결과, 심박수 기록.
- **Redis** — 사라져도 다시 만들 수 있는 것: refresh token, rate limit 카운터. (러닝 진행 중 상태도 원래 Redis 담당이었지만 **현재는 Postgres 행을 직접 갱신**합니다 — 실제로 Redis에 있는 건 refresh token뿐입니다.)

JWT는 access(단명) + refresh(Redis 저장) 2종. refresh를 Redis에 두는 이유는 로그아웃 시 즉시 무효화하기 위함입니다.

### 스키마 = Flyway (엔티티가 아님)

`ddl-auto: validate`입니다. `src/main/resources/db/migration/`의 V1~V9가 ERD 전체 테이블을 이미 정의해 두었습니다. **스키마를 바꿀 때는 엔티티만 고치지 말고 새 `V{n}__*.sql`을 추가하세요.** 이미 적용된 마이그레이션 파일은 수정하지 않습니다(checksum 불일치로 기동 실패). 엔티티와 SQL이 어긋나면 기동 시점에 validate로 걸립니다.

실제 마이그레이션에서 쓰고 있는 규칙:

- 테이블/컬럼은 snake_case. `User.userId`처럼 엔티티에 `@Column(name = ...)`을 명시합니다.
- PK는 UUID + `DEFAULT gen_random_uuid()`. 이름은 대체로 `{테이블단수}_id`지만 **예외 두 종류가 있습니다** — 1:1 테이블(`user_goals`, `notification_settings`, `integration_status`)은 `user_id`가 PK이자 FK이고, `recovery_actions`는 `recovery_action_id`가 아니라 `action_id`입니다.
- FK는 전부 `REFERENCES ... ON DELETE CASCADE`이고, 조회에 쓰이는 FK에는 `idx_{테이블}_{컬럼}` 인덱스를 겁니다.
- Enum 후보 컬럼(`intensity`, `status`, `heart_rate_source`, `sync_status`, `signal_quality`, `goal_type`, `type`)은 DB 타입이 아니라 `VARCHAR`입니다. 엔티티 쪽 `@Enumerated(EnumType.STRING)`과 짝을 이룹니다.
- 긴 자유 텍스트(`summary_message`, `description`)만 `TEXT`, 나머지는 길이를 명시한 `VARCHAR`.

### 도메인 모델

```
USERS ── 1:1 ── USER_GOALS / NOTIFICATION_SETTINGS / INTEGRATION_STATUS  (user_id가 PK이자 FK)
USERS ── 1:N ── RUNNING_SESSIONS, STRETCHING_SESSIONS
RUNNING_SESSIONS ── 1:N ── HEART_RATE_MEASUREMENTS   (워치 실패 시 rPPG 재측정 → 1:N)
RUNNING_SESSIONS ── 1:0..1 ── RECOVERY_GUIDES ── 1:N ── RECOVERY_ACTIONS
```

자세한 설명은 `docs/ERD_aftergrow.md`.

### 패키지 구조

도메인별로 나누고, 각 도메인 안에서 `controller/service/entity/repository/dto`로 계층을 나눕니다. DB 스키마(V1~V9)는 ERD 전체를 덮고 있지만 자바 코드는 아직 일부만 따라와 있습니다.

```
jungkathon3team.aftergrow
├── auth/       # 회원가입·로그인·재발급·로그아웃 ✔ 완료
├── home/       # GET /home 대시보드 ✔ (여러 도메인 집계)
├── running/    # prepare / 시작 / live / end + 스트레칭 ✔, external/에 UV·위치 목 구현
├── heartrate/  # 엔티티·리포지토리만 (홈 집계용). API 없음
├── profile/    # UserGoal 엔티티·리포지토리만 (홈 집계용). API 없음
├── recovery/   # 미착수 (패키지 자체가 없음)
└── common/     # ApiResponse<T>, ErrorCode, 예외 처리, SecurityConfig, OpenApiConfig
```

**엔티티는 필요한 필드만 채워 넣지 말고 테이블 전체를 매핑합니다** — `ddl-auto: validate`는 누락 컬럼을 잡지 않지만, 다음 도메인이 같은 테이블을 다시 열게 됩니다. 대신 아직 쓰이지 않는 enum 후보 컬럼(`sync_status`, `signal_quality`, `goal_type`)은 **String으로 두고, 실제로 쓰는 도메인에서 enum을 확정**합니다(`HeartRateMeasurement` 주석 참고).

### 엔티티 작성 규약

`User`가 기준 패턴입니다: `@Getter @Builder @NoArgsConstructor(PROTECTED) @AllArgsConstructor(PRIVATE)`, setter 없음, 생성 시각은 `@PrePersist`.

- 시각 필드는 `LocalDateTime` (DB는 `TIMESTAMP`). 문서 예시에 `Instant`가 보이면 코드 쪽을 따르세요.
  응답 JSON에 **타임존 오프셋이 붙지 않습니다**(`"2026-08-07T16:22:15.4986088"`). 명세서 예시는 `+09:00`이 붙어 있는데 실제와 다르며, 오프셋이 필요하면 `OffsetDateTime` + `TIMESTAMPTZ` 마이그레이션이 필요합니다. `AuthControllerTest`가 현재 형식을 고정하고 있습니다
- 연관관계는 항상 `fetch = FetchType.LAZY`
- Enum은 항상 `@Enumerated(EnumType.STRING)`

- `@ManyToOne(fetch = LAZY, optional = false)` + `@JoinColumn(name = "user_id", nullable = false)` (`RunningSession` 기준)
- PK는 `@GeneratedValue(strategy = GenerationType.UUID)`. 1:1 테이블(`UserGoal`)만 `@GeneratedValue` 없이 `user_id`를 그대로 씁니다.
- **상태 전이는 엔티티 메서드로** — `RunningSession.start()` 정적 팩토리 + `end()`/`complete()`/`updateLiveSnapshot()`. 서비스에서 setter로 필드를 만지지 않습니다.

`@EnableJpaAuditing`은 쓰지 않습니다. `createdAt`은 `@PrePersist` 수동 방식이고, 그 외 시각 필드(`startedAt`, `measuredAt`, `updatedAt`)는 호출자/DTO가 넘긴 값을 그대로 저장합니다.

### 공통 응답·에러

`common/`은 구현됐습니다 — `ApiResponse<T>`/`ApiError`, `ErrorCode` enum, `BusinessException`, `GlobalExceptionHandler`, `SecurityExceptionHandler`, `SecurityConfig`(+ `PasswordEncoder` 빈).

**에러 응답은 두 경로로 나옵니다.** 컨트롤러 진입 후 예외는 `GlobalExceptionHandler`(`@RestControllerAdvice`)가, 시큐리티 필터 단계의 인증/인가 실패는 `SecurityExceptionHandler`가 처리합니다. 후자는 컨트롤러 전이라 `@ControllerAdvice`가 못 잡으므로 직접 JSON을 씁니다 — 응답 형식을 바꿀 때 **두 곳 다** 고쳐야 합니다.

`ErrorCode`는 **API 명세서 §0 공통 에러 코드 표와 1:1로 대응**하며 `ErrorCodeTest`가 그 대응을 고정합니다. 명세서가 바뀌면 이 테스트부터 깨져야 합니다.

코드는 대체로 `E{HTTP상태코드}{일련번호}`지만 `E5010`만 502를 가리켜 규칙에서 벗어납니다(명세서 기준, 오타 여부 미확인). **새 코드를 만들 때 규칙을 유추하지 말고 명세서 값을 쓰세요.**

**응답은 성공/실패 모두 `ApiResponse`로 감쌉니다**(래퍼 A안). 명세서의 개별 엔드포인트 예시는 감싸지 않은 형태로 적혀 있는데, 그건 `data` 안쪽 내용으로 읽으면 됩니다. `success`/`data`/`error` 세 필드는 항상 존재합니다.

서비스는 예외를 던질 때 `throw new BusinessException(ErrorCode.X); // E40xx` 형태로 **주석에 코드 번호를 남기는 관례**가 있습니다(`RunningSessionService` 참고). 남의 리소스 접근은 404가 아니라 `FORBIDDEN`(E4030)입니다 — `getOwnedSession()`처럼 소유자 검사를 서비스 private 헬퍼로 빼세요.

아직 없는 것:

- **회복 가이드(recovery)** 도메인, 심박수/프로필 API. 다음은 심박수 → 회복 가이드 → 프로필 순.
- `RedisConfig` — `StringRedisTemplate` 자동 구성으로 충분해 아직 불필요합니다.
- **러닝 진행 상태의 Redis 저장** — 원래 설계는 `/live`를 Redis로 받는 것이었지만, 현재 구현은 Postgres의 `running_sessions` 행을 직접 갱신합니다(아래 참고).

### 러닝 / 홈 도메인에서 알아야 할 것

- `GET /running-sessions/{id}/live`는 **GET인데 쓰기를 합니다.** 명세에 진행 중 갱신용 엔드포인트가 따로 없어, 쿼리 파라미터로 딸려온 `distanceKm`/`intensity`를 `updateLiveSnapshot()`으로 반영하는 upsert-on-read 방식입니다(`@Transactional` 붙어 있음). 갱신은 `IN_PROGRESS`일 때만 일어납니다.
- `POST /running-sessions/{id}/end`는 **멱등**입니다. 이미 끝난 세션에 다시 호출하면 에러 대신 현재 상태를 그대로 돌려줍니다(명세에 전용 에러 코드가 없어서 내린 결정). 진행 중 세션이 있는데 새로 시작하면 `E4090`.
- `running/external/`의 `MockUvIndexClient`·`MockLocationLabelResolver`는 **인터페이스 뒤에 있는 임시 구현**입니다. 실제 API(기상청/Open-Meteo, 카카오 로컬)를 붙일 때 새 `@Component`를 만들고 목의 `@Component`를 제거하세요. UV 지수 → 라벨("낮음"…"위험") 변환은 `UvIndexClient.UvIndexResult.of()` 한 곳에만 두고 재구현하지 마세요 — 홈 주간 요약도 이걸 씁니다.
- `HomeService`의 주 단위는 **월요일 시작**(`TemporalAdjusters.previousOrSame(MONDAY)`)이고, "완료"는 `ENDED` + `COMPLETED` 둘 다입니다(`COMPLETED_STATUSES`). 새 집계를 추가할 땐 이 상수를 재사용하세요.
- 홈 집계 쿼리는 `HomeService`가 아니라 리포지토리의 `@Query`(`sumDistanceKmBetween`, `avgBpmBetween`, `avgUvIndexBetween`)에 있습니다. **합계는 `coalesce(...,0)`로 0을, 평균은 null을 반환**하는 게 의도된 구분입니다(측정 없음 = null로 응답).

### JWT

`jjwt 0.12.6`(HS256)을 씁니다. `auth/jwt/JwtTokenProvider`가 `application-local.yml`의 `jwt.secret` / `jwt.access-token-expiration-ms` / `jwt.refresh-token-expiration-ms`를 읽습니다.

- **secret은 256비트(32자) 이상**이어야 합니다. 짧으면 기동 시점에 바로 실패합니다.
- access/refresh는 `type` 클레임으로 구분되어 **서로 자리를 바꿔 쓸 수 없습니다.** 검증 실패(위조·만료·타입 불일치)는 전부 `E4010`으로 통일됩니다.
- refresh 토큰은 `auth/repository/RefreshTokenStore`가 Redis에 `refresh:{userId}` 키로 저장합니다(TTL = refresh 만료). 사용자당 하나만 유지되어 재로그인 시 이전 토큰이 덮어써집니다.
- **`/auth/refresh`는 서명 검증만으로 통과시키지 않습니다.** Redis 저장값과 일치해야 합니다(`matches`). 이 검사가 로그아웃을 실제로 동작하게 하는 유일한 지점이라 절대 빼면 안 됩니다 — 빼면 로그아웃해도 30일간 재발급이 됩니다.
- 로그아웃은 Redis 키를 지울 뿐, **이미 발급된 access 토큰은 만료 전까지 유효**합니다. JWT는 취소할 수 없습니다.
- **로그인 실패는 `E4011`이고, 이메일 없음과 비밀번호 불일치를 구분하지 않습니다.** 구분하면 가입된 이메일을 알아낼 수 있어서입니다. 이 동작은 테스트로 고정돼 있습니다.

Redis를 쓰는 테스트는 `@Transactional`로 롤백되지 않습니다 — 테스트에서 직접 키를 지워야 합니다.

**도메인 컨트롤러에서 로그인한 사용자를 받는 방법:**

```java
@GetMapping("/home")
public ApiResponse<HomeResponse> home(@AuthenticationPrincipal UUID userId) { ... }
```

`JwtAuthenticationFilter`가 토큰에서 꺼내 `SecurityContext`에 넣은 값이라 **클라이언트가 위조할 수 없습니다.** userId를 요청 본문이나 쿼리 파라미터로 받지 마세요 — 남의 데이터를 조회할 수 있게 됩니다.

필터는 **요청을 거절하지 않습니다.** 토큰이 없거나 잘못되면 아무것도 기록하지 않고 넘기고, 거절은 `SecurityConfig`의 인가 규칙과 `SecurityExceptionHandler`가 합니다. 그래야 잘못된 토큰이 딸려와도 permitAll 경로가 막히지 않습니다.

에러 코드는 API 명세서의 `E4001`, `E4010`, `E4030` 형식을 enum으로 정의해 씁니다.

## 협업 규칙

- 브랜치: `feature/{도메인}-{작업내용}` (예: `feature/auth-entity`, `feature/db-migration`).
- **문서상 전략은 `feature/*` → `develop` → `main`이지만, `develop` 브랜치는 로컬에도 origin에도 아직 없습니다.** 실제로는 PR #1이 `feature/db-migration` → `main`으로 바로 머지됐습니다. `develop`을 만들기 전까지는 PR 대상이 `main`입니다. CI 워크플로는 `main`/`develop` 대상 PR과 `develop` push에 걸려 있어서, `develop` push 트리거는 현재 발동하지 않습니다.
- 커밋: `feat|fix|refactor|test|docs|chore: 한국어 설명` (예: `feat: 러닝 세션 시작 API 구현`)
- `.env`, `application-local.yml`은 절대 커밋 금지. `docker-compose.yml`은 커밋되는 파일이므로 비밀번호를 직접 적지 말고 `${POSTGRES_PASSWORD}` 형태로만 참조합니다.

## 참고 문서

- `docs/API_명세.md` — 엔드포인트·요청/응답·공통 에러 코드 표. 새 API를 만들기 전에 여기부터.
- `docs/프로젝트_컨텍스트_jungkathon3team.md` — 기술 선택 이유, 트러블슈팅 기록, 진행 체크리스트. "왜 이렇게 됐지?" 싶을 때 여기부터.
- `docs/개발_시작_가이드.md` — 다음에 구현할 것(`common/`, 인증 순서, 테스트 전략)만 남긴 문서.
- `docs/ERD_aftergrow.md` — DB 구조와 설계 의도.
- `docs/백엔드_기술스택_노션용.md` — 스택 개요 및 AWS 배포 구상(미착수).
