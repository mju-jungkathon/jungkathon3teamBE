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

현재 테스트는 `AftergrowApplicationTests.contextLoads()` 하나뿐입니다. `User`/`UserRepository` 테스트는 아직 없습니다. 테스트 프로파일(`application-test.yml`)도 없어서 CI는 환경변수로만 주입합니다.

- 서버: http://localhost:8080 / Swagger UI: http://localhost:8080/swagger-ui.html
- Swagger와 `/auth/**`는 인증 없이 열려 있습니다(`common/config/SecurityConfig`의 `PUBLIC_PATHS`). 그 외 모든 경로는 인증이 필요하며, 아직 `JwtAuthenticationFilter`가 없어서 **현재는 인증 수단 자체가 없으므로 전부 401**입니다.
- lint/formatter 설정 없음(`.editorconfig`, checkstyle, spotless 모두 없음).

## 설정 구조

`application.yml`에는 앱 이름과 활성 프로파일(`local`)만 있고, **실제 datasource/redis/jwt 설정은 전부 `application-local.yml`에 있습니다. 이 파일은 `.env`와 함께 gitignore 대상입니다** — 클론 직후에는 존재하지 않으므로 직접 만들어야 하고, 값은 `.env`(원본은 `.env.example`)와 일치시켜야 합니다. 설정 키를 추가할 때는 `.env.example`에도 항목 이름을 추가해 두세요.

CI(`.github/workflows/test.yml`)는 `SPRING_PROFILES_ACTIVE=test` + 환경변수로 주입하며, `application-test.yml`은 아직 없습니다.

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
- **Redis** — 사라져도 다시 만들 수 있는 것: refresh token, 러닝 진행 중 실시간 상태(`/live` 폴링), rate limit 카운터.

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

도메인별로 나누고, 각 도메인 안에서 `controller/service/entity/repository/dto`로 계층을 나눕니다. **현재 실제로 존재하는 건 `auth/entity/User`와 `auth/repository/UserRepository` 둘뿐이고, 아래 나머지는 전부 계획 상태입니다** — DB 스키마(V1~V9)는 ERD 전체를 덮고 있지만 자바 코드는 `users` 테이블까지만 따라와 있습니다.

```
jungkathon3team.aftergrow
├── auth/       # 회원가입 ✔ (entity·repository·dto·service·controller) / 로그인·토큰 미구현
├── home/       # 홈 대시보드
├── running/    # RunningSession, StretchingSession
├── heartrate/  # HeartRateMeasurement
├── recovery/   # RecoveryGuide, RecoveryAction
├── profile/    # UserGoal, NotificationSetting, IntegrationStatus
└── common/     # ApiResponse<T>, 전역 예외 처리, SecurityConfig / RedisConfig
```

### 엔티티 작성 규약

`User`가 기준 패턴입니다: `@Getter @Builder @NoArgsConstructor(PROTECTED) @AllArgsConstructor(PRIVATE)`, setter 없음, 생성 시각은 `@PrePersist`.

- 시각 필드는 `LocalDateTime` (DB는 `TIMESTAMP`). 문서 예시에 `Instant`가 보이면 코드 쪽을 따르세요.
  응답 JSON에 **타임존 오프셋이 붙지 않습니다**(`"2026-08-07T16:22:15.4986088"`). 명세서 예시는 `+09:00`이 붙어 있는데 실제와 다르며, 오프셋이 필요하면 `OffsetDateTime` + `TIMESTAMPTZ` 마이그레이션이 필요합니다. `AuthControllerTest`가 현재 형식을 고정하고 있습니다
- 연관관계는 항상 `fetch = FetchType.LAZY`
- Enum은 항상 `@Enumerated(EnumType.STRING)`

아직 `User` 하나뿐이라, 두 번째 엔티티를 만들 때 `@ManyToOne` 매핑과 감사(auditing) 방식이 처음 결정됩니다. `createdAt`은 현재 `@PrePersist` 수동 방식이고 `@EnableJpaAuditing`은 쓰지 않습니다.

### 아직 없는 것 (구현 순서)

`common/`은 구현됐습니다 — `ApiResponse<T>`/`ApiError`, `ErrorCode` enum, `BusinessException`, `GlobalExceptionHandler`, `SecurityExceptionHandler`, `SecurityConfig`(+ `PasswordEncoder` 빈).

**에러 응답은 두 경로로 나옵니다.** 컨트롤러 진입 후 예외는 `GlobalExceptionHandler`(`@RestControllerAdvice`)가, 시큐리티 필터 단계의 인증/인가 실패는 `SecurityExceptionHandler`가 처리합니다. 후자는 컨트롤러 전이라 `@ControllerAdvice`가 못 잡으므로 직접 JSON을 씁니다 — 응답 형식을 바꿀 때 **두 곳 다** 고쳐야 합니다.

`ErrorCode`는 **API 명세서 §0 공통 에러 코드 표와 1:1로 대응**하며 `ErrorCodeTest`가 그 대응을 고정합니다. 명세서가 바뀌면 이 테스트부터 깨져야 합니다.

코드는 대체로 `E{HTTP상태코드}{일련번호}`지만 `E5010`만 502를 가리켜 규칙에서 벗어납니다(명세서 기준, 오타 여부 미확인). **새 코드를 만들 때 규칙을 유추하지 말고 명세서 값을 쓰세요.**

**응답은 성공/실패 모두 `ApiResponse`로 감쌉니다**(래퍼 A안). 명세서의 개별 엔드포인트 예시는 감싸지 않은 형태로 적혀 있는데, 그건 `data` 안쪽 내용으로 읽으면 됩니다. `success`/`data`/`error` 세 필드는 항상 존재합니다.

아직 없는 것:

- `RedisConfig` — refresh token 저장 단계에서 필요해지면 추가. Boot가 `RedisTemplate`을 자동 구성하므로 직렬화 커스터마이징이 실제로 필요할 때까지는 불필요합니다.
- 인증 — `POST /auth/signup` ✔ 완료. 다음은 `JwtTokenProvider` → `POST /auth/login` → `JwtAuthenticationFilter`(`OncePerRequestFilter`) → refresh/logout. **아직 토큰 발급이 없어서 `/auth/**`와 swagger 외 모든 경로는 통과할 방법이 없습니다.**
- 이후 프로필 → 러닝 세션 → 심박수 → 회복 가이드 → 홈 대시보드(여러 도메인 종합이라 마지막) 순.

JWT 관련 설정 키는 `application-local.yml`에 `jwt.secret` / `jwt.access-token-expiration-ms` / `jwt.refresh-token-expiration-ms`로 이미 자리를 잡아 뒀습니다(읽는 코드는 아직 없음).

에러 코드는 API 명세서의 `E4001`, `E4010`, `E4030` 형식을 enum으로 정의해 씁니다.

## 협업 규칙

- 브랜치: `feature/{도메인}-{작업내용}` (예: `feature/auth-entity`, `feature/db-migration`).
- **문서상 전략은 `feature/*` → `develop` → `main`이지만, `develop` 브랜치는 로컬에도 origin에도 아직 없습니다.** 실제로는 PR #1이 `feature/db-migration` → `main`으로 바로 머지됐습니다. `develop`을 만들기 전까지는 PR 대상이 `main`입니다. CI 워크플로는 `main`/`develop` 대상 PR과 `develop` push에 걸려 있어서, `develop` push 트리거는 현재 발동하지 않습니다.
- 커밋: `feat|fix|refactor|test|docs|chore: 한국어 설명` (예: `feat: 러닝 세션 시작 API 구현`)
- `.env`, `application-local.yml`은 절대 커밋 금지. `docker-compose.yml`은 커밋되는 파일이므로 비밀번호를 직접 적지 말고 `${POSTGRES_PASSWORD}` 형태로만 참조합니다.

## 참고 문서

`docs/`는 아직 git에 커밋되지 않은 상태(untracked)라 다른 사람이 클론하면 없습니다. 아래 참조는 이 워킹 트리 기준입니다.

- `docs/프로젝트_컨텍스트_jungkathon3team.md` — 기술 선택 이유, 트러블슈팅 기록, 진행 체크리스트. "왜 이렇게 됐지?" 싶을 때 여기부터.
- `docs/개발_시작_가이드.md` — 다음에 구현할 것(`common/`, 인증 순서, 테스트 전략)만 남긴 문서.
- `docs/ERD_aftergrow.md` — DB 구조와 설계 의도.
- `docs/백엔드_기술스택_노션용.md` — 스택 개요 및 AWS 배포 구상(미착수).
