# R4 심박수 측정 · R6 측정 기록 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AfterGrow 백엔드에 심박수 측정 흐름(R4)과 측정 기록 조회(R6) API 9개를 구현한다.

**Architecture:** 기존 `heartrate/` 패키지(엔티티·리포지토리만 존재)에 controller/service/dto 계층을 채운다. 새 DB 마이그레이션은 없다 — V7 `heart_rate_measurements`와 V4 `integration_status`가 이미 필요한 컬럼을 갖고 있다. rPPG 측정 중 상태(`rppgSessionId` → `runningSessionId`)는 Redis에 TTL 10분으로 두어 DB에 미완성 행이 남지 않게 한다.

**Tech Stack:** Java 17, Spring Boot 4.0.7 (`spring-boot-starter-webmvc`), Spring Security 7.0.6, Spring Data JPA, Redis(`StringRedisTemplate`), Flyway, Lombok, JUnit 5 + AssertJ.

**설계 문서:** `docs/superpowers/specs/2026-08-10-heartrate-api-design.md`

## Global Constraints

- **패키지명은 `aftergrow`다. `afterglow`가 아니다.** 새 파일마다 확인할 것.
- **Java 17 고정.** 바꾸지 않는다.
- **Spring Boot 4 import를 쓴다.** Boot 3 예제를 붙여넣으면 컴파일이 깨진다.
  - MockMvc 자동설정: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
  - Jackson: `tools.jackson.databind.ObjectMapper` (이 계획에서는 직접 쓰지 않음)
- **새 마이그레이션 파일을 만들지 않는다.** `ddl-auto: validate`이므로 엔티티가 기존 V4/V7 스키마와 정확히 일치해야 한다.
- **설정 키를 추가하지 않는다.** `application-local.yml` / `application-test.yml` 둘 다 손대지 않는다.
- **새 `ErrorCode`를 만들지 않는다.** `E4001`/`E4010`/`E4030`/`E4040`만 쓴다. `E5010 APPLE_HEALTH_SYNC_FAILED`는 enum에 남기되 사용처가 없어진다 (`ErrorCodeTest`가 명세 §0과의 1:1 대응을 고정 중이므로 삭제 금지).
- **엔티티 규약:** `@Getter @Builder @NoArgsConstructor(PROTECTED) @AllArgsConstructor(PRIVATE)`, setter 없음, 상태 전이는 엔티티 메서드로. 연관관계는 항상 `FetchType.LAZY`. Enum은 항상 `@Enumerated(EnumType.STRING)`.
- **시각 필드는 `LocalDateTime`** (DB는 `TIMESTAMP`). 응답 JSON에 타임존 오프셋이 붙지 않는다.
- **`userId`는 항상 `@AuthenticationPrincipal UUID userId`로 받는다.** 요청 본문이나 쿼리 파라미터로 받으면 남의 데이터를 조회할 수 있게 된다.
- **남의 리소스 접근은 404가 아니라 `FORBIDDEN`(E4030).**
- 서비스가 예외를 던질 때 `throw new BusinessException(ErrorCode.X); // E40xx` 형태로 주석에 코드 번호를 남긴다.
- 커밋 메시지: `feat|fix|refactor|test|docs|chore: 한국어 설명`
- 브랜치: `feature/heartrate-api` (이미 생성됨, PR 대상 `main`)

## 사전 조건

**모든 테스트 실행 전에 Docker 컨테이너가 떠 있어야 한다.** `@SpringBootTest`가 실제 PostgreSQL/Redis에 붙는다.

```bash
docker compose up -d
docker compose ps          # postgres/redis 가 healthy 인지 확인
```

컨테이너 없이 돌리면 HibernateException으로 실패한다. 빌드가 깨지면 이것부터 확인할 것.

---

## File Structure

### 신규 생성

| 파일 | 책임 |
|---|---|
| `heartrate/entity/SyncStatus.java` | 동기화 상태 enum |
| `heartrate/entity/SignalQuality.java` | rPPG 신호 품질 enum |
| `heartrate/repository/RppgSessionStore.java` | 측정 중 `rppgSessionId` → `runningSessionId` 매핑 (Redis) |
| `heartrate/dto/HeartRateMeasurementResponse.java` | 4.2/4.6 공용 측정 결과 응답 |
| `heartrate/dto/SelectSourceDto.java` | 4.1 |
| `heartrate/dto/AppleHealthDto.java` | 4.2 · 4.3 |
| `heartrate/dto/RppgGuideResponse.java` | 4.4 (문구·시간 상수 보유) |
| `heartrate/dto/RppgStartDto.java` | 4.5 |
| `heartrate/dto/RppgResultDto.java` | 4.6 |
| `heartrate/dto/HeartRateRecordsResponse.java` | 6.1 |
| `heartrate/dto/RetryResponse.java` | 6.2 |
| `heartrate/service/HeartRateMeasurementService.java` | R4·R6 전 로직 + 기본 source 파생 |
| `heartrate/controller/HeartRateMeasurementController.java` | 4.4 · 4.5 · 4.6 · 6.1 · 6.2 |
| `heartrate/controller/AppleHealthController.java` | 4.2 · 4.3 |
| `profile/entity/IntegrationStatus.java` | V4 `integration_status` 매핑 |
| `profile/repository/IntegrationStatusRepository.java` | 위 리포지토리 |

### 신규 테스트

| 파일 | 덮는 것 | 태스크 |
|---|---|---|
| `heartrate/entity/HeartRateMeasurementTest.java` | POOR → bpm/hrv null (순수 단위, Spring 없음) | 1 |
| `profile/entity/IntegrationStatusTest.java` | `linkAppleHealth` (순수 단위) | 2 |
| `heartrate/repository/RppgSessionStoreTest.java` | Redis 저장/조회/삭제/TTL | 3 |
| `heartrate/service/HeartRateMeasurementServiceTest.java` | `range` 파싱 · 6.1 목록 · `sourceRatio` 집계 | 5, 6 |
| `heartrate/service/HeartRateRppgFlowTest.java` | 4.4·4.5·4.6 rPPG 흐름 · 6.2 재측정 | 7 |
| `heartrate/service/HeartRateAppleHealthTest.java` | 4.1·4.2·4.3 · 기본 source 파생 | 8 |
| `heartrate/controller/HeartRateControllerTest.java` | 엔드포인트 인가 · 응답 래핑 | 9 |
| `running/RunningEndDefaultSourceTest.java` | `/end` 응답의 `defaultHeartRateSource` | 10 |

설계 문서 §7은 "한 파일"이라 했지만 8개로 나뉜다. Redis를 쓰는 흐름은 `@Transactional` 롤백이 통하지 않아 `@AfterEach` 정리가 필요하고, 순수 단위 테스트는 Spring 컨텍스트 없이 도는 편이 빠르며, 컨트롤러 테스트는 `@AutoConfigureMockMvc`가 따로 필요하다. 덮는 내용은 설계 문서 §7의 네 가지를 모두 포함한다.

### 수정

| 파일 | 변경 |
|---|---|
| `heartrate/entity/HeartRateMeasurement.java` | `String` 2개 → enum, 정적 팩토리 `watch()`/`rppg()` 추가 |
| `heartrate/repository/HeartRateMeasurementRepository.java` | 6.1 목록 조회 쿼리 추가 |
| `running/dto/RunningEndDto.java` | `Response`에 `defaultHeartRateSource` 추가 |
| `running/service/RunningSessionService.java` | `HeartRateMeasurementService` 주입, `/end` 응답에 기본 source 채움 |
| `running/controller/RunningSessionController.java` | 4.1 `select-source` 엔드포인트 추가 |
| `docs/API_명세.md` | R4 변경점(GET→POST, authorize→link, 기본값) 반영 |

### 설계 문서 대비 조정 1건

설계 문서 §5.1은 `RunningSessionService`가 `HeartRateMeasurementRepository` + `IntegrationStatusRepository`를 직접 주입받는다고 썼다. **대신 `HeartRateMeasurementService.defaultSourceFor(userId)`를 호출하도록 바꾼다.** 주입 대상이 둘에서 하나로 줄고, 설계 문서 §7의 테스트 네 가지가 `HeartRateMeasurementServiceTest` 한 파일에 모인다. 순환 의존은 생기지 않는다 — `HeartRateMeasurementService`는 `RunningSessionRepository`(서비스가 아님)에만 의존한다.

---

## Task 1: 심박수 enum 2개와 엔티티 정적 팩토리

`heart_rate_measurements`의 `sync_status` / `signal_quality`를 String에서 enum으로 확정한다. V7 컬럼이 이미 `VARCHAR`이므로 마이그레이션이 없다.

POOR 측정값의 bpm/hrv를 null로 만드는 규칙을 **엔티티 안에** 둔다. 서비스에 두면 잊기 쉽고, `HeartRateMeasurementRepository.avgBpmBetween`(R2 홈 대시보드의 주간 평균 bpm)이 `sync_status`를 거르지 않아 홈 화면 평균이 오염된다.

**Files:**
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/entity/SyncStatus.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/entity/SignalQuality.java`
- Modify: `src/main/java/jungkathon3team/aftergrow/heartrate/entity/HeartRateMeasurement.java`
- Test: `src/test/java/jungkathon3team/aftergrow/heartrate/entity/HeartRateMeasurementTest.java`

**Interfaces:**
- Consumes: 기존 `HeartRateSource` (`WATCH`, `RPPG`), `RunningSession`
- Produces:
  - `enum SyncStatus { SUCCESS, FAILED }`
  - `enum SignalQuality { GOOD, POOR }`
  - `static HeartRateMeasurement HeartRateMeasurement.watch(RunningSession session, Integer avgBpm, Integer maxBpm, Integer hrvMs, LocalDateTime measuredAt)`
  - `static HeartRateMeasurement HeartRateMeasurement.rppg(RunningSession session, Integer avgBpm, Integer maxBpm, Integer hrvMs, LocalDateTime measuredAt, SignalQuality signalQuality)`
  - `SyncStatus getSyncStatus()`, `SignalQuality getSignalQuality()` (Lombok `@Getter`)

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/jungkathon3team/aftergrow/heartrate/entity/HeartRateMeasurementTest.java`:

```java
package jungkathon3team.aftergrow.heartrate.entity;

import jungkathon3team.aftergrow.running.entity.RunningSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 컨텍스트 없이 도는 순수 단위 테스트.
 * POOR 측정값을 null로 지우는 규칙이 여기서 깨지면 R2 홈 대시보드의 주간 평균 bpm까지 오염된다.
 */
class HeartRateMeasurementTest {

    private static final LocalDateTime MEASURED_AT = LocalDateTime.of(2026, 8, 4, 7, 42);
    private final RunningSession session = RunningSession.start(
            null, LocalDateTime.of(2026, 8, 4, 7, 0), 37.5, 127.0, 5);

    @Test
    void 워치_측정은_항상_SUCCESS로_저장된다() {
        HeartRateMeasurement m = HeartRateMeasurement.watch(session, 152, 168, 42, MEASURED_AT);

        assertThat(m.getHeartRateSource()).isEqualTo(HeartRateSource.WATCH);
        assertThat(m.getSyncStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(m.getAvgBpm()).isEqualTo(152);
        assertThat(m.getMaxBpm()).isEqualTo(168);
        assertThat(m.getHrvMs()).isEqualTo(42);
        assertThat(m.getMeasuredAt()).isEqualTo(MEASURED_AT);
    }

    @Test
    void 워치_측정에는_신호_품질이_없다() {
        HeartRateMeasurement m = HeartRateMeasurement.watch(session, 152, 168, 42, MEASURED_AT);

        assertThat(m.getSignalQuality()).isNull();
    }

    @Test
    void 신호_품질이_GOOD인_rPPG는_값을_그대로_저장한다() {
        HeartRateMeasurement m = HeartRateMeasurement.rppg(
                session, 146, 158, 38, MEASURED_AT, SignalQuality.GOOD);

        assertThat(m.getHeartRateSource()).isEqualTo(HeartRateSource.RPPG);
        assertThat(m.getSyncStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(m.getSignalQuality()).isEqualTo(SignalQuality.GOOD);
        assertThat(m.getAvgBpm()).isEqualTo(146);
        assertThat(m.getMaxBpm()).isEqualTo(158);
        assertThat(m.getHrvMs()).isEqualTo(38);
    }

    /** 신뢰할 수 없는 값이 홈 대시보드 평균에 섞이지 않도록 버린다. */
    @Test
    void 신호_품질이_POOR인_rPPG는_FAILED로_저장하고_측정값을_버린다() {
        HeartRateMeasurement m = HeartRateMeasurement.rppg(
                session, 146, 158, 38, MEASURED_AT, SignalQuality.POOR);

        assertThat(m.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(m.getSignalQuality()).isEqualTo(SignalQuality.POOR);
        assertThat(m.getAvgBpm()).isNull();
        assertThat(m.getMaxBpm()).isNull();
        assertThat(m.getHrvMs()).isNull();
    }

    /** 측정 시각은 실패해도 남아야 한다. 화면 8에 "언제 실패했는지"가 표시된다. */
    @Test
    void POOR이어도_측정_시각은_남는다() {
        HeartRateMeasurement m = HeartRateMeasurement.rppg(
                session, 146, 158, 38, MEASURED_AT, SignalQuality.POOR);

        assertThat(m.getMeasuredAt()).isEqualTo(MEASURED_AT);
        assertThat(m.getRunningSession()).isSameAs(session);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests '*HeartRateMeasurementTest'
```

Expected: 컴파일 실패 — `SyncStatus`, `SignalQuality`, `HeartRateMeasurement.watch`, `HeartRateMeasurement.rppg` 를 찾을 수 없음.

- [ ] **Step 3: enum 2개를 만든다**

`src/main/java/jungkathon3team/aftergrow/heartrate/entity/SyncStatus.java`:

```java
package jungkathon3team.aftergrow.heartrate.entity;

/**
 * 심박수 측정 기록의 동기화 상태.
 * <p>API 명세 R4.6 / R6.1 기준. 측정 중 상태(PENDING)는 두지 않는다 —
 * rPPG 측정 중에는 Redis({@code rppg:{id}})만 존재하고 DB에는 행이 만들어지지 않는다.
 */
public enum SyncStatus {
    SUCCESS,
    FAILED
}
```

`src/main/java/jungkathon3team/aftergrow/heartrate/entity/SignalQuality.java`:

```java
package jungkathon3team.aftergrow.heartrate.entity;

/**
 * rPPG 측정의 신호 품질. 워치(WATCH) 측정에는 해당 없음(null).
 * <p>POOR면 측정값을 신뢰할 수 없어 {@link SyncStatus#FAILED}로 저장하고 bpm/hrv를 버린다.
 */
public enum SignalQuality {
    GOOD,
    POOR
}
```

- [ ] **Step 4: 엔티티를 수정한다**

`HeartRateMeasurement.java`에서 필드 2개의 타입을 바꾸고 정적 팩토리를 추가한다. 클래스 Javadoc도 갱신한다 (기존 "R4/R6에서 확장 예정", "String으로 둔다" 문장이 더는 맞지 않는다).

기존:

```java
    @Column(name = "sync_status", length = 20)
    private String syncStatus;

    @Column(name = "signal_quality", length = 20)
    private String signalQuality;
}
```

교체:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", length = 20)
    private SyncStatus syncStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_quality", length = 20)
    private SignalQuality signalQuality;

    /**
     * R4.2 워치(애플 헬스) 측정. 앱이 HealthKit 읽기에 성공했을 때만 업로드하므로 항상 SUCCESS다.
     * 신호 품질은 rPPG에만 있는 개념이라 null로 둔다.
     */
    public static HeartRateMeasurement watch(RunningSession runningSession,
                                             Integer avgBpm,
                                             Integer maxBpm,
                                             Integer hrvMs,
                                             LocalDateTime measuredAt) {
        return HeartRateMeasurement.builder()
                .runningSession(runningSession)
                .heartRateSource(HeartRateSource.WATCH)
                .avgBpm(avgBpm)
                .maxBpm(maxBpm)
                .hrvMs(hrvMs)
                .measuredAt(measuredAt)
                .syncStatus(SyncStatus.SUCCESS)
                .build();
    }

    /**
     * R4.6 rPPG 측정 결과.
     * <p>신호 품질이 POOR이면 FAILED로 저장하고 bpm/hrv를 버린다.
     * 값을 그대로 두면 {@code HeartRateMeasurementRepository.avgBpmBetween}이
     * sync_status를 거르지 않으므로 R2 홈 대시보드의 주간 평균 bpm이 오염된다.
     */
    public static HeartRateMeasurement rppg(RunningSession runningSession,
                                            Integer avgBpm,
                                            Integer maxBpm,
                                            Integer hrvMs,
                                            LocalDateTime measuredAt,
                                            SignalQuality signalQuality) {
        boolean usable = signalQuality != SignalQuality.POOR;
        return HeartRateMeasurement.builder()
                .runningSession(runningSession)
                .heartRateSource(HeartRateSource.RPPG)
                .avgBpm(usable ? avgBpm : null)
                .maxBpm(usable ? maxBpm : null)
                .hrvMs(usable ? hrvMs : null)
                .measuredAt(measuredAt)
                .syncStatus(usable ? SyncStatus.SUCCESS : SyncStatus.FAILED)
                .signalQuality(signalQuality)
                .build();
    }
}
```

클래스 Javadoc을 아래로 교체한다:

```java
/**
 * heart_rate_measurements 테이블 매핑.
 * <p>워치 실패 시 rPPG 재측정으로 한 러닝 세션에 여러 건이 남을 수 있어 RunningSession : HeartRateMeasurement = 1:N.
 * <p>R2(홈 대시보드)는 최근 1건과 이번 주 평균 bpm을, R4/R6는 측정 기록 생성과 목록 조회에 사용한다.
 * <p>행은 R4.2(워치 업로드) 또는 R4.6(rPPG 결과 제출)에서만 만들어진다.
 * rPPG 측정 중 상태는 Redis({@code RppgSessionStore})가 들고 있어 미완성 행이 남지 않는다.
 */
```

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*HeartRateMeasurementTest'
```

Expected: PASS (5개).

- [ ] **Step 6: 전체 테스트로 회귀를 확인한다**

`sync_status`/`signal_quality` 타입 변경이 다른 곳을 깨지 않았는지 본다.

```bash
./gradlew test
```

Expected: 기존 51개 + 신규 5개 모두 PASS. `HomeService`는 이 두 필드를 읽지 않으므로 영향이 없어야 한다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/heartrate/entity/ \
        src/test/java/jungkathon3team/aftergrow/heartrate/entity/
git commit -m "feat: 심박수 측정 enum 확정 및 엔티티 정적 팩토리 추가"
```

---

## Task 2: IntegrationStatus 엔티티와 리포지토리

V4 `integration_status`를 매핑한다. 지금 쓰는 건 `apple_health_linked` 하나지만 **네 컬럼을 전부 매핑한다** — R7 프로필이 같은 테이블을 다시 연다.

`UserGoal`처럼 1:1 테이블이라 `@GeneratedValue` 없이 `user_id`를 PK로 그대로 쓴다.

**Files:**
- Create: `src/main/java/jungkathon3team/aftergrow/profile/entity/IntegrationStatus.java`
- Create: `src/main/java/jungkathon3team/aftergrow/profile/repository/IntegrationStatusRepository.java`
- Test: `src/test/java/jungkathon3team/aftergrow/profile/entity/IntegrationStatusTest.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `static IntegrationStatus IntegrationStatus.of(UUID userId)` — 모든 플래그 false로 새 행
  - `void IntegrationStatus.linkAppleHealth(boolean linked)`
  - `boolean IntegrationStatus.isAppleHealthLinked()` (Lombok `@Getter`)
  - `interface IntegrationStatusRepository extends JpaRepository<IntegrationStatus, UUID>`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/jungkathon3team/aftergrow/profile/entity/IntegrationStatusTest.java`:

```java
package jungkathon3team.aftergrow.profile.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationStatusTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void 새로_만들면_모든_연동이_꺼져_있다() {
        IntegrationStatus status = IntegrationStatus.of(userId);

        assertThat(status.getUserId()).isEqualTo(userId);
        assertThat(status.isAppleHealthLinked()).isFalse();
        assertThat(status.isLocationLinked()).isFalse();
        assertThat(status.isCameraPermission()).isFalse();
        assertThat(status.isLocationPermission()).isFalse();
    }

    @Test
    void 애플_헬스_연동을_켤_수_있다() {
        IntegrationStatus status = IntegrationStatus.of(userId);

        status.linkAppleHealth(true);

        assertThat(status.isAppleHealthLinked()).isTrue();
    }

    /** 사용자가 iOS 설정에서 권한을 회수하면 앱이 false로 다시 보낸다. */
    @Test
    void 애플_헬스_연동을_끌_수_있다() {
        IntegrationStatus status = IntegrationStatus.of(userId);
        status.linkAppleHealth(true);

        status.linkAppleHealth(false);

        assertThat(status.isAppleHealthLinked()).isFalse();
    }

    @Test
    void 애플_헬스_연동은_다른_연동_상태를_건드리지_않는다() {
        IntegrationStatus status = IntegrationStatus.of(userId);

        status.linkAppleHealth(true);

        assertThat(status.isLocationLinked()).isFalse();
        assertThat(status.isCameraPermission()).isFalse();
        assertThat(status.isLocationPermission()).isFalse();
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests '*IntegrationStatusTest'
```

Expected: 컴파일 실패 — `IntegrationStatus` 클래스 없음.

- [ ] **Step 3: 엔티티를 만든다**

V4 스키마를 다시 확인하고 (`src/main/resources/db/migration/V4__create_integration_status_table.sql`) 컬럼 이름을 정확히 맞춘다. `ddl-auto: validate`이므로 어긋나면 기동 시점에 실패한다.

`src/main/java/jungkathon3team/aftergrow/profile/entity/IntegrationStatus.java`:

```java
package jungkathon3team.aftergrow.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * integration_status 테이블 매핑. USERS와 1:1이며 {@code user_id}가 PK이자 FK.
 * <p>R4.3(애플 헬스 연동 기록)에서는 {@code apple_health_linked}만 사용하지만,
 * R7(프로필/설정)이 같은 테이블을 다시 열기 때문에 네 컬럼을 모두 매핑해 둔다.
 * <p>DB 컬럼이 모두 {@code NOT NULL DEFAULT false}라 필드는 원시 타입 {@code boolean}이다.
 */
@Entity
@Table(name = "integration_status")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntegrationStatus {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "location_linked", nullable = false)
    private boolean locationLinked;

    @Column(name = "camera_permission", nullable = false)
    private boolean cameraPermission;

    @Column(name = "location_permission", nullable = false)
    private boolean locationPermission;

    @Column(name = "apple_health_linked", nullable = false)
    private boolean appleHealthLinked;

    /** 연동 정보가 아직 없는 사용자의 새 행. 모든 플래그가 꺼진 상태다. */
    public static IntegrationStatus of(UUID userId) {
        return IntegrationStatus.builder()
                .userId(userId)
                .build();
    }

    /**
     * R4.3. 앱이 HealthKit 권한 동의 결과를 알려올 때 호출한다.
     * 사용자가 iOS 설정에서 권한을 회수하면 false로도 들어온다.
     */
    public void linkAppleHealth(boolean linked) {
        this.appleHealthLinked = linked;
    }
}
```

- [ ] **Step 4: 리포지토리를 만든다**

`src/main/java/jungkathon3team/aftergrow/profile/repository/IntegrationStatusRepository.java`:

```java
package jungkathon3team.aftergrow.profile.repository;

import jungkathon3team.aftergrow.profile.entity.IntegrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IntegrationStatusRepository extends JpaRepository<IntegrationStatus, UUID> {
}
```

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*IntegrationStatusTest'
```

Expected: PASS (4개).

- [ ] **Step 6: 새 엔티티가 스키마 검증을 통과하는지 확인한다**

`ddl-auto: validate`가 새 엔티티를 V4 테이블과 대조한다. 컬럼명이 하나라도 어긋나면 컨텍스트 로딩이 실패한다.

```bash
./gradlew test --tests '*AftergrowApplicationTests'
```

Expected: PASS. 실패하면 `SchemaManagementException` 메시지의 컬럼명을 V4와 다시 맞춘다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/profile/ \
        src/test/java/jungkathon3team/aftergrow/profile/
git commit -m "feat: IntegrationStatus 엔티티 및 리포지토리 추가"
```

---

## Task 3: rPPG 측정 세션 저장소 (Redis)

명세 4.6의 요청 본문에는 `runningSessionId`가 없다. 서버가 `rppgSessionId` → `runningSessionId` 매핑을 4.5와 4.6 사이에 들고 있어야 한다.

Postgres에 PENDING 행을 미리 만들지 않는 이유: 사용자가 측정 도중 앱을 끄면 빈 행이 영구히 남고, 6.1 목록과 `sourceRatio` 집계에서 매번 걸러내야 한다. Redis는 TTL로 알아서 사라진다.

**Files:**
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/repository/RppgSessionStore.java`
- Test: `src/test/java/jungkathon3team/aftergrow/heartrate/repository/RppgSessionStoreTest.java`

**Interfaces:**
- Consumes: `StringRedisTemplate` (Spring Boot 자동 구성, `RedisConfig` 불필요)
- Produces:
  - `void RppgSessionStore.save(UUID rppgSessionId, UUID runningSessionId)`
  - `Optional<UUID> RppgSessionStore.findRunningSessionId(UUID rppgSessionId)`
  - `void RppgSessionStore.delete(UUID rppgSessionId)`
  - `RppgSessionStore.KEY_PREFIX` (`"rppg:"`, 테스트에서 키를 직접 지울 때 사용)

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/jungkathon3team/aftergrow/heartrate/repository/RppgSessionStoreTest.java`:

```java
package jungkathon3team.aftergrow.heartrate.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis는 @Transactional로 롤백되지 않으므로 테스트에서 직접 키를 지운다.
 */
@SpringBootTest
class RppgSessionStoreTest {

    @Autowired
    private RppgSessionStore rppgSessionStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final UUID rppgSessionId = UUID.randomUUID();
    private final UUID runningSessionId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        redisTemplate.delete(RppgSessionStore.KEY_PREFIX + rppgSessionId);
    }

    @Test
    void 저장한_러닝_세션_id를_다시_꺼낼_수_있다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        assertThat(rppgSessionStore.findRunningSessionId(rppgSessionId))
                .contains(runningSessionId);
    }

    @Test
    void 저장하지_않은_id를_조회하면_비어_있다() {
        assertThat(rppgSessionStore.findRunningSessionId(UUID.randomUUID()))
                .isEmpty();
    }

    /** 같은 rppgSessionId로 결과를 두 번 제출할 수 없어야 한다. */
    @Test
    void 삭제하면_더_이상_조회되지_않는다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        rppgSessionStore.delete(rppgSessionId);

        assertThat(rppgSessionStore.findRunningSessionId(rppgSessionId)).isEmpty();
    }

    @Test
    void 없는_키를_삭제해도_조용히_넘어간다() {
        rppgSessionStore.delete(UUID.randomUUID());
    }

    /** 측정을 끝내지 않고 앱을 꺼도 키가 영원히 남지 않아야 한다. */
    @Test
    void 저장된_키에는_만료_시간이_걸려_있다() {
        rppgSessionStore.save(rppgSessionId, runningSessionId);

        Long ttlSeconds = redisTemplate.getExpire(RppgSessionStore.KEY_PREFIX + rppgSessionId);

        assertThat(ttlSeconds).isNotNull().isPositive().isLessThanOrEqualTo(600);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Docker 컨테이너가 떠 있어야 한다.

```bash
docker compose ps
./gradlew test --tests '*RppgSessionStoreTest'
```

Expected: 컴파일 실패 — `RppgSessionStore` 클래스 없음.

- [ ] **Step 3: 저장소를 만든다**

`RefreshTokenStore`와 같은 패턴을 따른다.

`src/main/java/jungkathon3team/aftergrow/heartrate/repository/RppgSessionStore.java`:

```java
package jungkathon3team.aftergrow.heartrate.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * rPPG 측정 중(R4.5 → R4.6)에만 필요한 {@code rppgSessionId → runningSessionId} 매핑을 Redis에 보관합니다.
 * <p>
 * R4.6 요청 본문에 runningSessionId가 없어 서버가 이 매핑을 들고 있어야 합니다.
 * 측정을 끝내지 않고 앱을 꺼도 TTL로 사라지므로, Postgres에 미완성 측정 행이 남지 않습니다
 * (남으면 R6.1 목록과 sourceRatio 집계에서 매번 걸러내야 합니다).
 */
@Repository
@RequiredArgsConstructor
public class RppgSessionStore {

    public static final String KEY_PREFIX = "rppg:";

    /** 측정 자체는 12초지만, 네트워크 지연·앱 전환을 감안해 넉넉히 잡습니다. */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public void save(UUID rppgSessionId, UUID runningSessionId) {
        redisTemplate.opsForValue().set(key(rppgSessionId), runningSessionId.toString(), TTL);
    }

    /** 만료됐거나 애초에 없던 id면 비어 있습니다. 호출자는 E4040으로 응답합니다. */
    public Optional<UUID> findRunningSessionId(UUID rppgSessionId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(rppgSessionId)))
                .map(UUID::fromString);
    }

    /** 결과 제출 후 삭제합니다. 같은 rppgSessionId로 두 번 제출할 수 없게 하는 지점입니다. */
    public void delete(UUID rppgSessionId) {
        redisTemplate.delete(key(rppgSessionId));
    }

    private String key(UUID rppgSessionId) {
        return KEY_PREFIX + rppgSessionId;
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*RppgSessionStoreTest'
```

Expected: PASS (5개).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/heartrate/repository/RppgSessionStore.java \
        src/test/java/jungkathon3team/aftergrow/heartrate/repository/
git commit -m "feat: rPPG 측정 세션 Redis 저장소 추가"
```

---

## Task 4: 응답 DTO 7개

컨트롤러/서비스보다 먼저 만들어 둔다. 이후 Task 5~8이 전부 이 타입들을 참조한다.

4.2와 4.6의 응답이 명세상 동일하므로 `HeartRateMeasurementResponse` 하나를 공유한다.

**Files:**
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/dto/HeartRateMeasurementResponse.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/dto/SelectSourceDto.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/dto/AppleHealthDto.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/dto/RppgGuideResponse.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/dto/RppgStartDto.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/dto/RppgResultDto.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/dto/HeartRateRecordsResponse.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/dto/RetryResponse.java`
- Test: 없음 (값 타입만 있고 로직이 없다. `RppgGuideResponse.defaults()`와 `HeartRateMeasurementResponse.from()`은 Task 5~8의 서비스 테스트로 함께 덮인다.)

**Interfaces:**
- Consumes: `HeartRateSource`, `SyncStatus`, `SignalQuality`, `HeartRateMeasurement` (Task 1)
- Produces: 아래 모든 record 타입과 상수. Task 5~9가 이 이름들을 그대로 쓴다.

- [ ] **Step 1: 공용 측정 결과 응답을 만든다**

`heartrate/dto/HeartRateMeasurementResponse.java`:

```java
package jungkathon3team.aftergrow.heartrate.dto;

import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;

import java.util.UUID;

/**
 * R4.2(워치 업로드)와 R4.6(rPPG 결과 제출)의 응답. 명세상 두 응답이 동일해 하나를 공유한다.
 * <p>신호 품질이 POOR이면 엔티티 단계에서 bpm/hrv가 null이 되어 그대로 내려간다.
 */
public record HeartRateMeasurementResponse(
        UUID heartRateMeasurementId,
        HeartRateSource heartRateSource,
        Integer avgBpm,
        Integer maxBpm,
        Integer hrvMs,
        SyncStatus syncStatus
) {
    public static HeartRateMeasurementResponse from(HeartRateMeasurement measurement) {
        return new HeartRateMeasurementResponse(
                measurement.getHeartRateMeasurementId(),
                measurement.getHeartRateSource(),
                measurement.getAvgBpm(),
                measurement.getMaxBpm(),
                measurement.getHrvMs(),
                measurement.getSyncStatus()
        );
    }
}
```

- [ ] **Step 2: 4.1 측정 방식 선택 DTO를 만든다**

`heartrate/dto/SelectSourceDto.java`:

```java
package jungkathon3team.aftergrow.heartrate.dto;

import jakarta.validation.constraints.NotNull;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;

/** R4.1 POST /running-sessions/{id}/heart-rate/select-source */
public class SelectSourceDto {

    public record Request(
            @NotNull HeartRateSource heartRateSource
    ) {}

    public record Response(
            HeartRateSource heartRateSource,
            String nextStep
    ) {
        public static final String NEXT_STEP_FETCH_APPLE_HEALTH = "FETCH_APPLE_HEALTH";
        public static final String NEXT_STEP_RPPG_GUIDE = "RPPG_GUIDE";
    }
}
```

- [ ] **Step 3: 4.2 / 4.3 애플 헬스 DTO를 만든다**

`heartrate/dto/AppleHealthDto.java`:

```java
package jungkathon3team.aftergrow.heartrate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R4.2 워치 데이터 업로드 · R4.3 애플 헬스 연동 기록.
 * <p>명세는 두 엔드포인트를 GET으로 적었지만 HealthKit은 온디바이스 API라 서버가 직접 읽을 수 없다.
 * 앱이 읽은 값과 권한 동의 결과를 서버로 올리는 POST 구조로 바꿨다.
 */
public class AppleHealthDto {

    public record HeartRateRequest(
            @NotNull UUID runningSessionId,
            @NotNull @Positive Integer avgBpm,
            @NotNull @Positive Integer maxBpm,
            Integer hrvMs,
            @NotNull LocalDateTime syncedAt
    ) {}

    public record LinkRequest(
            @NotNull Boolean linked
    ) {}

    public record LinkResponse(
            boolean appleHealthLinked
    ) {}
}
```

`hrvMs`에 `@NotNull`을 붙이지 않는다 — 기기·측정 조건에 따라 HRV가 안 나올 수 있고, V7 컬럼도 nullable이다.

- [ ] **Step 4: 4.4 rPPG 안내 응답을 만든다**

`heartrate/dto/RppgGuideResponse.java`:

```java
package jungkathon3team.aftergrow.heartrate.dto;

/**
 * R4.4 GET /heart-rate-measurements/rppg/guide
 * <p>고정 문구다. {@link #DURATION_SEC}는 R4.5 응답도 함께 쓴다 — 두 곳의 값이 달라지면 안 된다.
 */
public record RppgGuideResponse(
        String instruction,
        int durationSec
) {
    public static final String INSTRUCTION = "후면 카메라와 플래시에 손가락을 밀착시켜 약 12초간 측정해요";
    public static final int DURATION_SEC = 12;

    public static RppgGuideResponse defaults() {
        return new RppgGuideResponse(INSTRUCTION, DURATION_SEC);
    }
}
```

- [ ] **Step 5: 4.5 / 4.6 rPPG DTO를 만든다**

`heartrate/dto/RppgStartDto.java`:

```java
package jungkathon3team.aftergrow.heartrate.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** R4.5 POST /heart-rate-measurements/rppg/start */
public class RppgStartDto {

    public record Request(
            @NotNull UUID runningSessionId
    ) {}

    public record Response(
            UUID rppgSessionId,
            String status,
            int durationSec
    ) {
        public static final String STATUS_MEASURING = "MEASURING";
    }
}
```

`heartrate/dto/RppgResultDto.java`:

```java
package jungkathon3team.aftergrow.heartrate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;

import java.time.LocalDateTime;

/**
 * R4.6 POST /heart-rate-measurements/rppg/{rppgSessionId}/result
 * <p>카메라 원본 영상은 서버로 보내지 않는다. 온디바이스 rPPG 알고리즘의 결과값만 올라온다.
 * <p>응답은 {@link HeartRateMeasurementResponse}를 쓴다.
 */
public class RppgResultDto {

    public record Request(
            @NotNull @Positive Integer avgBpm,
            @NotNull @Positive Integer maxBpm,
            Integer hrvMs,
            @NotNull LocalDateTime measuredAt,
            @NotNull SignalQuality signalQuality
    ) {}
}
```

`signalQuality`가 POOR이어도 `avgBpm`/`maxBpm`은 `@NotNull @Positive`로 받는다. 앱은 측정한 값을 그대로 올리고, **버리는 판단은 서버(`HeartRateMeasurement.rppg()`)가 한다.**

- [ ] **Step 6: 6.1 / 6.2 DTO를 만든다**

`heartrate/dto/HeartRateRecordsResponse.java`:

```java
package jungkathon3team.aftergrow.heartrate.dto;

import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * R6.1 GET /heart-rate-measurements?range=30d
 * <p>명세 예시의 sourceRatio는 records 건수와 맞지 않는다(예시가 잘린 것으로 본다).
 * 같은 range 안의 실제 건수로 정의하며, rppgFailedCount는 rppg의 부분집합이다.
 */
public record HeartRateRecordsResponse(
        List<Item> records,
        SourceRatio sourceRatio
) {
    /** {@code Record}라는 이름은 {@code java.lang.Record}를 가려서 쓰지 않는다. */
    public record Item(
            UUID heartRateMeasurementId,
            LocalDateTime measuredAt,
            HeartRateSource heartRateSource,
            Integer avgBpm,
            UUID runningSessionId,
            SyncStatus syncStatus
    ) {
        public static Item from(HeartRateMeasurement measurement) {
            return new Item(
                    measurement.getHeartRateMeasurementId(),
                    measurement.getMeasuredAt(),
                    measurement.getHeartRateSource(),
                    measurement.getAvgBpm(),
                    measurement.getRunningSession().getRunningSessionId(),
                    measurement.getSyncStatus()
            );
        }
    }

    public record SourceRatio(
            long watch,
            long rppg,
            long rppgFailedCount
    ) {}
}
```

`heartrate/dto/RetryResponse.java`:

```java
package jungkathon3team.aftergrow.heartrate.dto;

import java.util.UUID;

/**
 * R6.2 POST /heart-rate-measurements/{id}/retry
 * <p>앱은 이 응답을 받고 R4.4~4.6 rPPG 흐름을 다시 탄다. 실패 기록 자체는 삭제하지 않는다.
 */
public record RetryResponse(
        String retryFlow,
        UUID runningSessionId
) {
    public static final String RETRY_FLOW_RPPG_GUIDE = "RPPG_GUIDE";
}
```

- [ ] **Step 7: 컴파일을 확인한다**

DTO만 추가했으므로 테스트는 늘지 않는다. 컴파일이 통과하는지만 본다.

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/heartrate/dto/
git commit -m "feat: 심박수 측정·기록 API DTO 추가"
```

---

## Task 5: 리포지토리 조회 쿼리와 range 파싱

6.1 목록 조회에 필요한 쿼리를 추가하고, `range` 파라미터 파싱을 서비스의 package-private static 메서드로 만든다.

static으로 두는 이유는 순수 함수이고 DB 없이 직접 테스트할 수 있기 때문이다. 같은 패키지의 테스트에서 인스턴스 없이 호출한다.

**Files:**
- Modify: `src/main/java/jungkathon3team/aftergrow/heartrate/repository/HeartRateMeasurementRepository.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementService.java` (뼈대 + `sinceOf`)
- Test: `src/test/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementServiceTest.java` (신규)

**Interfaces:**
- Consumes: `HeartRateMeasurement` (Task 1), `BusinessException`/`ErrorCode` (기존)
- Produces:
  - `List<HeartRateMeasurement> HeartRateMeasurementRepository.findByRunningSession_User_UserIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDesc(UUID userId, LocalDateTime since)`
  - `static LocalDateTime HeartRateMeasurementService.sinceOf(String range, LocalDateTime now)` (package-private)
  - `HeartRateMeasurementService.DEFAULT_RANGE` (`"30d"`, package-private)
  - `@Service class HeartRateMeasurementService` — Task 6~8이 메서드를 채운다

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementServiceTest.java`:

```java
package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R4/R6 서비스 로직 테스트.
 * <p>Task 5에서는 range 파싱만 덮는다. sourceRatio 집계와 기본 source 파생은 이후 태스크에서 추가된다.
 */
class HeartRateMeasurementServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 12, 0);

    // --- range 파싱 ---

    @Test
    void range가_30d면_30일_전부터_조회한다() {
        assertThat(HeartRateMeasurementService.sinceOf("30d", NOW))
                .isEqualTo(LocalDateTime.of(2026, 7, 11, 12, 0));
    }

    @Test
    void range가_7d면_7일_전부터_조회한다() {
        assertThat(HeartRateMeasurementService.sinceOf("7d", NOW))
                .isEqualTo(LocalDateTime.of(2026, 8, 3, 12, 0));
    }

    @Test
    void range를_생략하면_기본_30일이다() {
        assertThat(HeartRateMeasurementService.sinceOf(null, NOW))
                .isEqualTo(HeartRateMeasurementService.sinceOf("30d", NOW));
    }

    @Test
    void range가_빈_문자열이면_기본_30일이다() {
        assertThat(HeartRateMeasurementService.sinceOf("  ", NOW))
                .isEqualTo(HeartRateMeasurementService.sinceOf("30d", NOW));
    }

    @Test
    void 형식이_어긋난_range는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("abc", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 단위가_없는_range는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("30", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 주_단위처럼_지원하지_않는_단위는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("4w", NOW))
                .isInstanceOf(BusinessException.class);
    }

    // 자바 식별자는 숫자로 시작할 수 없어 앞에 _를 붙인다.
    @Test
    void _0일_조회는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("0d", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 음수_range는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("-5d", NOW))
                .isInstanceOf(BusinessException.class);
    }

    /** long 범위를 넘는 숫자에 NumberFormatException이 새어 나가면 500이 된다. */
    @Test
    void 지나치게_큰_숫자도_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("99999999999999999999d", NOW))
                .isInstanceOf(BusinessException.class);
    }
}
```

`BusinessException`은 `private final ErrorCode errorCode` + Lombok `@Getter`이므로 `extracting("errorCode")`가 그대로 동작한다 (확인 완료).

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
./gradlew test --tests '*HeartRateMeasurementServiceTest'
```

Expected: 컴파일 실패 — `HeartRateMeasurementService` 클래스 없음.

- [ ] **Step 3: 리포지토리에 조회 쿼리를 추가한다**

`HeartRateMeasurementRepository.java`의 기존 메서드는 그대로 두고 아래를 추가한다. import에 `java.util.List`를 넣는다.

```java
    /**
     * R6.1 측정 기록 목록: 사용자의 range 내 측정 기록을 최신순으로.
     * <p>sourceRatio는 이 목록을 자바에서 세서 만든다(30일치면 많아야 수십 건).
     */
    List<HeartRateMeasurement> findByRunningSession_User_UserIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDesc(
            UUID userId, LocalDateTime since);
```

- [ ] **Step 4: 서비스 뼈대와 `sinceOf`를 만든다**

`src/main/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementService.java`:

```java
package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.heartrate.repository.RppgSessionStore;
import jungkathon3team.aftergrow.profile.repository.IntegrationStatusRepository;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R4 심박수 측정 · R6 측정 기록.
 * <p>남의 러닝 세션/측정 기록 접근은 404가 아니라 E4030이다(소유 여부를 노출하지 않기 위함).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeartRateMeasurementService {

    static final String DEFAULT_RANGE = "30d";

    /** 지원 형식은 "{일수}d" 하나뿐이다. 주/월 단위는 명세에 없어 받지 않는다. */
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)d");

    private final HeartRateMeasurementRepository heartRateMeasurementRepository;
    private final RunningSessionRepository runningSessionRepository;
    private final IntegrationStatusRepository integrationStatusRepository;
    private final RppgSessionStore rppgSessionStore;

    /**
     * R6.1의 {@code range} 파라미터를 조회 하한 시각으로 바꾼다.
     * <p>순수 함수라 DB 없이 테스트한다. 생략/공백이면 기본 30일.
     */
    static LocalDateTime sinceOf(String range, LocalDateTime now) {
        String value = (range == null || range.isBlank()) ? DEFAULT_RANGE : range.trim();

        Matcher matcher = RANGE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST); // E4001
        }

        long days;
        try {
            days = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            // long을 넘는 자릿수. 그대로 두면 500이 되므로 잘못된 요청으로 돌린다.
            throw new BusinessException(ErrorCode.INVALID_REQUEST); // E4001
        }

        if (days <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST); // E4001
        }

        return now.minusDays(days);
    }
}
```

주입 필드 4개는 Task 6~8에서 전부 쓰인다. 지금은 `sinceOf`만 있어 미사용 경고가 날 수 있으나 컴파일에는 문제가 없다.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*HeartRateMeasurementServiceTest'
```

Expected: PASS (10개).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/heartrate/repository/HeartRateMeasurementRepository.java \
        src/main/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementService.java \
        src/test/java/jungkathon3team/aftergrow/heartrate/service/
git commit -m "feat: 측정 기록 조회 쿼리 및 range 파싱 추가"
```

---

## Task 6: 6.1 목록 조회와 sourceRatio 집계

**Files:**
- Modify: `src/main/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementService.java`
- Test: `src/test/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementServiceTest.java` (테스트 추가)

**Interfaces:**
- Consumes: `sinceOf` (Task 5), `HeartRateRecordsResponse` (Task 4), `HeartRateMeasurement.watch/rppg` (Task 1)
- Produces: `HeartRateRecordsResponse HeartRateMeasurementService.getRecords(UUID userId, String range)`

- [ ] **Step 1: 실패하는 테스트를 추가한다**

`HeartRateMeasurementServiceTest`를 `@SpringBootTest @Transactional`로 바꾸고 DB 픽스처를 쓰는 테스트를 추가한다. Task 5의 `sinceOf` 테스트는 그대로 둔다 (static 메서드라 컨텍스트가 떠 있어도 그대로 돈다).

클래스 선언과 import를 아래로 교체한다:

```java
package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateRecordsResponse;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R4/R6 서비스 로직 테스트.
 * <p>range 파싱은 static 메서드라 컨텍스트 없이도 돌지만, sourceRatio 집계와 기본 source 파생은
 * 실제 DB 픽스처가 필요해 한 클래스에 함께 둔다.
 */
@SpringBootTest
@Transactional
class HeartRateMeasurementServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 12, 0);

    @Autowired
    private HeartRateMeasurementService heartRateMeasurementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    private UUID userId;
    private RunningSession session;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("hr-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        userId = user.getUserId();
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    /** daysAgo일 전에 측정된 기록을 만든다. */
    private void saveWatch(int daysAgo, int avgBpm) {
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, avgBpm, avgBpm + 15, 42, LocalDateTime.now().minusDays(daysAgo)));
    }

    private void saveRppg(int daysAgo, int avgBpm, SignalQuality quality) {
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, avgBpm, avgBpm + 12, 38, LocalDateTime.now().minusDays(daysAgo), quality));
    }
```

(Task 5에서 쓴 `sinceOf` 테스트 10개는 그대로 둔 채) 아래 테스트를 클래스 안에 추가한다:

```java
    // --- 6.1 목록 조회 ---

    @Test
    void 측정_기록이_없으면_빈_목록과_0_집계를_반환한다() {
        HeartRateRecordsResponse response = heartRateMeasurementService.getRecords(userId, "30d");

        assertThat(response.records()).isEmpty();
        assertThat(response.sourceRatio().watch()).isZero();
        assertThat(response.sourceRatio().rppg()).isZero();
        assertThat(response.sourceRatio().rppgFailedCount()).isZero();
    }

    @Test
    void 측정_기록을_최신순으로_반환한다() {
        saveWatch(5, 150);
        saveRppg(1, 146, SignalQuality.GOOD);
        saveWatch(10, 152);

        HeartRateRecordsResponse response = heartRateMeasurementService.getRecords(userId, "30d");

        assertThat(response.records())
                .extracting(HeartRateRecordsResponse.Item::measuredAt)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(response.records()).hasSize(3);
    }

    @Test
    void range_밖의_기록은_제외한다() {
        saveWatch(3, 150);
        saveWatch(40, 148);

        HeartRateRecordsResponse response = heartRateMeasurementService.getRecords(userId, "30d");

        assertThat(response.records()).hasSize(1);
        assertThat(response.sourceRatio().watch()).isEqualTo(1);
    }

    @Test
    void sourceRatio는_측정_방식별_건수를_센다() {
        saveWatch(1, 152);
        saveWatch(2, 150);
        saveRppg(3, 146, SignalQuality.GOOD);
        saveRppg(4, 140, SignalQuality.POOR);

        HeartRateRecordsResponse.SourceRatio ratio =
                heartRateMeasurementService.getRecords(userId, "30d").sourceRatio();

        assertThat(ratio.watch()).isEqualTo(2);
        assertThat(ratio.rppg()).isEqualTo(2);
        assertThat(ratio.rppgFailedCount()).isEqualTo(1);
    }

    /** rppgFailedCount는 rppg에서 따로 빠지는 게 아니라 부분집합이다. */
    @Test
    void 실패한_rPPG도_rppg_건수에_포함된다() {
        saveRppg(1, 140, SignalQuality.POOR);
        saveRppg(2, 141, SignalQuality.POOR);

        HeartRateRecordsResponse.SourceRatio ratio =
                heartRateMeasurementService.getRecords(userId, "30d").sourceRatio();

        assertThat(ratio.rppg()).isEqualTo(2);
        assertThat(ratio.rppgFailedCount()).isEqualTo(2);
        assertThat(ratio.watch()).isZero();
    }

    @Test
    void 실패한_기록은_avgBpm이_null로_내려간다() {
        saveRppg(1, 140, SignalQuality.POOR);

        HeartRateRecordsResponse.Item item =
                heartRateMeasurementService.getRecords(userId, "30d").records().get(0);

        assertThat(item.syncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(item.avgBpm()).isNull();
        assertThat(item.heartRateSource()).isEqualTo(HeartRateSource.RPPG);
        assertThat(item.runningSessionId()).isEqualTo(session.getRunningSessionId());
    }

    /** 남의 측정 기록이 섞이면 개인정보 유출이다. */
    @Test
    void 다른_사용자의_기록은_조회되지_않는다() {
        saveWatch(1, 152);
        User other = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("남")
                .build());

        HeartRateRecordsResponse response =
                heartRateMeasurementService.getRecords(other.getUserId(), "30d");

        assertThat(response.records()).isEmpty();
    }

    @Test
    void 잘못된_range로_목록을_조회하면_E4001이다() {
        assertThatThrownBy(() -> heartRateMeasurementService.getRecords(userId, "abc"))
                .isInstanceOf(BusinessException.class);
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
docker compose ps
./gradlew test --tests '*HeartRateMeasurementServiceTest'
```

Expected: 컴파일 실패 — `getRecords` 메서드 없음.

- [ ] **Step 3: `getRecords`를 구현한다**

`HeartRateMeasurementService`에 추가한다. import에 `HeartRateRecordsResponse`, `HeartRateMeasurement`, `HeartRateSource`, `SyncStatus`, `java.util.List`를 넣는다.

```java
    /**
     * R6.1 GET /heart-rate-measurements?range=30d
     * <p>sourceRatio는 별도 집계 쿼리 없이 조회된 목록을 세서 만든다(30일치면 많아야 수십 건).
     */
    public HeartRateRecordsResponse getRecords(UUID userId, String range) {
        LocalDateTime since = sinceOf(range, LocalDateTime.now());

        List<HeartRateMeasurement> measurements = heartRateMeasurementRepository
                .findByRunningSession_User_UserIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDesc(userId, since);

        List<HeartRateRecordsResponse.Item> items = measurements.stream()
                .map(HeartRateRecordsResponse.Item::from)
                .toList();

        return new HeartRateRecordsResponse(items, sourceRatioOf(measurements));
    }

    /** rppgFailedCount는 rppg에서 빠지는 값이 아니라 부분집합이다. */
    private HeartRateRecordsResponse.SourceRatio sourceRatioOf(List<HeartRateMeasurement> measurements) {
        long watch = measurements.stream()
                .filter(m -> m.getHeartRateSource() == HeartRateSource.WATCH)
                .count();
        long rppg = measurements.stream()
                .filter(m -> m.getHeartRateSource() == HeartRateSource.RPPG)
                .count();
        long rppgFailed = measurements.stream()
                .filter(m -> m.getHeartRateSource() == HeartRateSource.RPPG)
                .filter(m -> m.getSyncStatus() == SyncStatus.FAILED)
                .count();

        return new HeartRateRecordsResponse.SourceRatio(watch, rppg, rppgFailed);
    }
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*HeartRateMeasurementServiceTest'
```

Expected: PASS (18개 — Task 5의 10개 + 신규 8개).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementService.java \
        src/test/java/jungkathon3team/aftergrow/heartrate/service/
git commit -m "feat: 심박수 측정 기록 목록 조회 및 sourceRatio 집계 구현"
```

---

## Task 7: rPPG 측정 흐름 (4.4 · 4.5 · 4.6)과 재측정 (6.2)

**Files:**
- Modify: `src/main/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementService.java`
- Test: `src/test/java/jungkathon3team/aftergrow/heartrate/service/HeartRateRppgFlowTest.java` (신규 — Redis를 쓰므로 `@Transactional` 롤백이 통하지 않아 별도 클래스로 분리한다)

**Interfaces:**
- Consumes: `RppgSessionStore` (Task 3), `RppgGuideResponse`/`RppgStartDto`/`RppgResultDto`/`HeartRateMeasurementResponse`/`RetryResponse` (Task 4)
- Produces:
  - `RppgGuideResponse HeartRateMeasurementService.rppgGuide()`
  - `RppgStartDto.Response HeartRateMeasurementService.startRppg(UUID userId, RppgStartDto.Request request)`
  - `HeartRateMeasurementResponse HeartRateMeasurementService.submitRppgResult(UUID userId, UUID rppgSessionId, RppgResultDto.Request request)`
  - `RetryResponse HeartRateMeasurementService.retry(UUID userId, UUID measurementId)`
  - `private RunningSession HeartRateMeasurementService.getOwnedSession(UUID userId, UUID sessionId)`

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/jungkathon3team/aftergrow/heartrate/service/HeartRateRppgFlowTest.java`:

```java
package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateMeasurementResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgGuideResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgResultDto;
import jungkathon3team.aftergrow.heartrate.dto.RppgStartDto;
import jungkathon3team.aftergrow.heartrate.dto.RetryResponse;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.heartrate.repository.RppgSessionStore;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R4.4~4.6 rPPG 흐름과 R6.2 재측정.
 * <p>Redis는 @Transactional로 롤백되지 않으므로 발급한 rppgSessionId를 직접 지운다.
 */
@SpringBootTest
@Transactional
class HeartRateRppgFlowTest {

    @Autowired
    private HeartRateMeasurementService heartRateMeasurementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    @Autowired
    private RppgSessionStore rppgSessionStore;

    private UUID userId;
    private RunningSession session;
    private final List<UUID> issuedRppgSessionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("rppg-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        userId = user.getUserId();
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    @AfterEach
    void tearDown() {
        issuedRppgSessionIds.forEach(rppgSessionStore::delete);
    }

    private UUID startRppg() {
        UUID id = heartRateMeasurementService
                .startRppg(userId, new RppgStartDto.Request(session.getRunningSessionId()))
                .rppgSessionId();
        issuedRppgSessionIds.add(id);
        return id;
    }

    private RppgResultDto.Request result(SignalQuality quality) {
        return new RppgResultDto.Request(146, 158, 38, LocalDateTime.now(), quality);
    }

    // --- 4.4 안내 ---

    @Test
    void rPPG_안내는_고정_문구와_측정_시간을_반환한다() {
        RppgGuideResponse guide = heartRateMeasurementService.rppgGuide();

        assertThat(guide.durationSec()).isEqualTo(12);
        assertThat(guide.instruction()).isNotBlank();
    }

    // --- 4.5 측정 시작 ---

    @Test
    void 측정을_시작하면_MEASURING_상태와_측정_시간을_반환한다() {
        RppgStartDto.Response response = heartRateMeasurementService
                .startRppg(userId, new RppgStartDto.Request(session.getRunningSessionId()));
        issuedRppgSessionIds.add(response.rppgSessionId());

        assertThat(response.rppgSessionId()).isNotNull();
        assertThat(response.status()).isEqualTo("MEASURING");
        assertThat(response.durationSec()).isEqualTo(RppgGuideResponse.DURATION_SEC);
    }

    /** 측정 중에는 DB에 행이 만들어지면 안 된다. 중단해도 미완성 기록이 남지 않아야 한다. */
    @Test
    void 측정_시작만으로는_측정_기록이_생기지_않는다() {
        startRppg();

        assertThat(heartRateMeasurementRepository
                .findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(userId))
                .isEmpty();
    }

    @Test
    void 없는_러닝_세션으로_측정을_시작하면_E4040이다() {
        assertThatThrownBy(() -> heartRateMeasurementService
                .startRppg(userId, new RppgStartDto.Request(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 남의_러닝_세션으로_측정을_시작하면_E4030이다() {
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("남")
                .build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService
                .startRppg(otherUserId, new RppgStartDto.Request(session.getRunningSessionId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // --- 4.6 결과 제출 ---

    @Test
    void 품질이_GOOD이면_측정값을_그대로_저장한다() {
        UUID rppgSessionId = startRppg();

        HeartRateMeasurementResponse response = heartRateMeasurementService
                .submitRppgResult(userId, rppgSessionId, result(SignalQuality.GOOD));

        assertThat(response.heartRateSource()).isEqualTo(HeartRateSource.RPPG);
        assertThat(response.syncStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(response.avgBpm()).isEqualTo(146);
        assertThat(response.heartRateMeasurementId()).isNotNull();
    }

    @Test
    void 품질이_POOR이면_FAILED로_저장하고_측정값을_버린다() {
        UUID rppgSessionId = startRppg();

        HeartRateMeasurementResponse response = heartRateMeasurementService
                .submitRppgResult(userId, rppgSessionId, result(SignalQuality.POOR));

        assertThat(response.syncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(response.avgBpm()).isNull();
        assertThat(response.maxBpm()).isNull();
        assertThat(response.hrvMs()).isNull();
    }

    @Test
    void 결과를_제출하면_측정_기록이_러닝_세션에_연결된다() {
        UUID rppgSessionId = startRppg();

        UUID measurementId = heartRateMeasurementService
                .submitRppgResult(userId, rppgSessionId, result(SignalQuality.GOOD))
                .heartRateMeasurementId();

        HeartRateMeasurement saved = heartRateMeasurementRepository.findById(measurementId).orElseThrow();
        assertThat(saved.getRunningSession().getRunningSessionId())
                .isEqualTo(session.getRunningSessionId());
    }

    /** 같은 rppgSessionId로 두 번 제출하면 측정 기록이 중복으로 쌓인다. */
    @Test
    void 같은_측정_세션으로_두_번_제출하면_E4040이다() {
        UUID rppgSessionId = startRppg();
        heartRateMeasurementService.submitRppgResult(userId, rppgSessionId, result(SignalQuality.GOOD));

        assertThatThrownBy(() -> heartRateMeasurementService
                .submitRppgResult(userId, rppgSessionId, result(SignalQuality.GOOD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 발급되지_않은_측정_세션으로_제출하면_E4040이다() {
        assertThatThrownBy(() -> heartRateMeasurementService
                .submitRppgResult(userId, UUID.randomUUID(), result(SignalQuality.GOOD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 남의_측정_세션에_결과를_제출하면_E4030이다() {
        UUID rppgSessionId = startRppg();
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("남")
                .build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService
                .submitRppgResult(otherUserId, rppgSessionId, result(SignalQuality.GOOD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // --- 6.2 재측정 ---

    @Test
    void 재측정은_rPPG_안내_흐름과_러닝_세션을_알려준다() {
        HeartRateMeasurement failed = heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 140, 150, 30, LocalDateTime.now(), SignalQuality.POOR));

        RetryResponse response = heartRateMeasurementService
                .retry(userId, failed.getHeartRateMeasurementId());

        assertThat(response.retryFlow()).isEqualTo("RPPG_GUIDE");
        assertThat(response.runningSessionId()).isEqualTo(session.getRunningSessionId());
    }

    /** 실패 이력은 화면 8에 남아야 한다. RUNNING_SESSIONS : MEASUREMENTS가 1:N인 이유다. */
    @Test
    void 재측정해도_실패_기록은_삭제되지_않는다() {
        HeartRateMeasurement failed = heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 140, 150, 30, LocalDateTime.now(), SignalQuality.POOR));

        heartRateMeasurementService.retry(userId, failed.getHeartRateMeasurementId());

        assertThat(heartRateMeasurementRepository.findById(failed.getHeartRateMeasurementId()))
                .isPresent();
    }

    @Test
    void 없는_측정_기록을_재측정하면_E4040이다() {
        assertThatThrownBy(() -> heartRateMeasurementService.retry(userId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 남의_측정_기록을_재측정하면_E4030이다() {
        HeartRateMeasurement mine = heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 140, 150, 30, LocalDateTime.now(), SignalQuality.POOR));
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("남")
                .build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService
                .retry(otherUserId, mine.getHeartRateMeasurementId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
docker compose ps
./gradlew test --tests '*HeartRateRppgFlowTest'
```

Expected: 컴파일 실패 — `rppgGuide`, `startRppg`, `submitRppgResult`, `retry` 메서드 없음.

- [ ] **Step 3: 소유자 검증 헬퍼를 추가한다**

`HeartRateMeasurementService`에 private 헬퍼를 추가한다. `RunningSessionService.getOwnedSession()`과 같은 방식이며, private 헬퍼 몇 줄을 공유하려고 도메인 간 서비스 의존을 만들지 않는다.

```java
    /**
     * 세션을 찾고 소유자를 확인한다.
     * <p>남의 세션은 404가 아니라 E4030이다 — 존재 여부 자체를 노출하지 않기 위함.
     */
    private RunningSession getOwnedSession(UUID userId, UUID sessionId) {
        RunningSession session = runningSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040
        if (!session.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // E4030
        }
        return session;
    }
```

import에 `jungkathon3team.aftergrow.running.entity.RunningSession`을 넣는다.

- [ ] **Step 4: 4.4 / 4.5 / 4.6 / 6.2를 구현한다**

```java
    /** R4.4 GET /heart-rate-measurements/rppg/guide — 고정 안내 문구. */
    public RppgGuideResponse rppgGuide() {
        return RppgGuideResponse.defaults();
    }

    /**
     * R4.5 POST /heart-rate-measurements/rppg/start
     * <p>DB에는 아무것도 쓰지 않는다. 측정 중 매핑만 Redis에 남기고,
     * 측정 기록은 R4.6 결과 제출에서 처음 생성된다.
     */
    public RppgStartDto.Response startRppg(UUID userId, RppgStartDto.Request request) {
        RunningSession session = getOwnedSession(userId, request.runningSessionId());

        UUID rppgSessionId = UUID.randomUUID();
        rppgSessionStore.save(rppgSessionId, session.getRunningSessionId());

        return new RppgStartDto.Response(
                rppgSessionId,
                RppgStartDto.Response.STATUS_MEASURING,
                RppgGuideResponse.DURATION_SEC
        );
    }

    /**
     * R4.6 POST /heart-rate-measurements/rppg/{rppgSessionId}/result
     * <p>신호 품질이 POOR이면 FAILED로 저장된다(값을 버리는 판단은 엔티티가 한다).
     * 실패도 에러가 아니라 "재측정이 필요한 기록"이라 201로 응답한다.
     * <p>Redis 키는 제출 후 삭제해 같은 rppgSessionId로 두 번 제출할 수 없게 한다.
     */
    @Transactional
    public HeartRateMeasurementResponse submitRppgResult(UUID userId,
                                                         UUID rppgSessionId,
                                                         RppgResultDto.Request request) {
        UUID runningSessionId = rppgSessionStore.findRunningSessionId(rppgSessionId)
                // 만료됐거나 이미 제출됐거나 애초에 없던 id. 어느 세션의 것인지 알 수 없어 404다.
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040

        RunningSession session = getOwnedSession(userId, runningSessionId);

        HeartRateMeasurement measurement = heartRateMeasurementRepository.save(
                HeartRateMeasurement.rppg(
                        session,
                        request.avgBpm(),
                        request.maxBpm(),
                        request.hrvMs(),
                        request.measuredAt(),
                        request.signalQuality()
                ));

        rppgSessionStore.delete(rppgSessionId);

        return HeartRateMeasurementResponse.from(measurement);
    }

    /**
     * R6.2 POST /heart-rate-measurements/{id}/retry
     * <p>실패 기록을 삭제하지 않는다. 재측정 성공 행이 따로 쌓이고 실패 이력은 화면 8에 남는다.
     * <p>syncStatus가 FAILED가 아닌 기록에 호출해도 막지 않는다 — 명세에 전용 에러 코드가 없고,
     * 멀쩡한 측정을 다시 하겠다는 것을 거부할 이유가 없다.
     */
    public RetryResponse retry(UUID userId, UUID measurementId) {
        HeartRateMeasurement measurement = heartRateMeasurementRepository.findById(measurementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040

        RunningSession session = measurement.getRunningSession();
        if (!session.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // E4030
        }

        return new RetryResponse(
                RetryResponse.RETRY_FLOW_RPPG_GUIDE,
                session.getRunningSessionId()
        );
    }
```

import에 `HeartRateMeasurementResponse`, `RppgGuideResponse`, `RppgResultDto`, `RppgStartDto`, `RetryResponse`, `HeartRateMeasurement`를 넣는다.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*HeartRateRppgFlowTest'
```

Expected: PASS (16개).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementService.java \
        src/test/java/jungkathon3team/aftergrow/heartrate/service/HeartRateRppgFlowTest.java
git commit -m "feat: rPPG 측정 흐름 및 실패 기록 재측정 구현"
```

---

## Task 8: 애플 헬스 (4.2 · 4.3)와 측정 방식 선택 (4.1), 기본 source 파생

**Files:**
- Modify: `src/main/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementService.java`
- Test: `src/test/java/jungkathon3team/aftergrow/heartrate/service/HeartRateAppleHealthTest.java` (신규)

**Interfaces:**
- Consumes: `IntegrationStatus`/`IntegrationStatusRepository` (Task 2), `AppleHealthDto`/`SelectSourceDto`/`HeartRateMeasurementResponse` (Task 4), `getOwnedSession` (Task 7)
- Produces:
  - `SelectSourceDto.Response HeartRateMeasurementService.selectSource(UUID userId, UUID sessionId, SelectSourceDto.Request request)`
  - `HeartRateMeasurementResponse HeartRateMeasurementService.uploadWatchMeasurement(UUID userId, AppleHealthDto.HeartRateRequest request)`
  - `AppleHealthDto.LinkResponse HeartRateMeasurementService.linkAppleHealth(UUID userId, AppleHealthDto.LinkRequest request)`
  - `HeartRateSource HeartRateMeasurementService.defaultSourceFor(UUID userId)` — Task 10이 `/end` 응답에서 호출한다

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/jungkathon3team/aftergrow/heartrate/service/HeartRateAppleHealthTest.java`:

```java
package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.dto.AppleHealthDto;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateMeasurementResponse;
import jungkathon3team.aftergrow.heartrate.dto.SelectSourceDto;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateSource;
import jungkathon3team.aftergrow.heartrate.entity.SignalQuality;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.profile.entity.IntegrationStatus;
import jungkathon3team.aftergrow.profile.repository.IntegrationStatusRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R4.1 측정 방식 선택 · R4.2 워치 업로드 · R4.3 연동 기록 · 기본 측정 방식 파생. */
@SpringBootTest
@Transactional
class HeartRateAppleHealthTest {

    @Autowired
    private HeartRateMeasurementService heartRateMeasurementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    @Autowired
    private IntegrationStatusRepository integrationStatusRepository;

    private UUID userId;
    private RunningSession session;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("ah-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        userId = user.getUserId();
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    // --- 4.1 측정 방식 선택 ---

    @Test
    void 워치를_고르면_애플_헬스_조회로_분기한다() {
        SelectSourceDto.Response response = heartRateMeasurementService.selectSource(
                userId, session.getRunningSessionId(),
                new SelectSourceDto.Request(HeartRateSource.WATCH));

        assertThat(response.heartRateSource()).isEqualTo(HeartRateSource.WATCH);
        assertThat(response.nextStep()).isEqualTo("FETCH_APPLE_HEALTH");
    }

    @Test
    void rPPG를_고르면_측정_안내로_분기한다() {
        SelectSourceDto.Response response = heartRateMeasurementService.selectSource(
                userId, session.getRunningSessionId(),
                new SelectSourceDto.Request(HeartRateSource.RPPG));

        assertThat(response.nextStep()).isEqualTo("RPPG_GUIDE");
    }

    /** 선택값은 저장하지 않는다. 이후 흐름(4.2/4.6)이 각자 source를 확정한다. */
    @Test
    void 측정_방식을_골라도_측정_기록이_생기지_않는다() {
        heartRateMeasurementService.selectSource(userId, session.getRunningSessionId(),
                new SelectSourceDto.Request(HeartRateSource.RPPG));

        assertThat(heartRateMeasurementRepository
                .findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(userId))
                .isEmpty();
    }

    @Test
    void 남의_세션에서_측정_방식을_고르면_E4030이다() {
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed").nickname("남").build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService.selectSource(
                otherUserId, session.getRunningSessionId(),
                new SelectSourceDto.Request(HeartRateSource.RPPG)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // --- 4.2 워치 데이터 업로드 ---

    @Test
    void 워치_데이터를_업로드하면_SUCCESS로_저장된다() {
        HeartRateMeasurementResponse response = heartRateMeasurementService.uploadWatchMeasurement(
                userId, new AppleHealthDto.HeartRateRequest(
                        session.getRunningSessionId(), 152, 168, 42,
                        LocalDateTime.of(2026, 8, 4, 6, 55)));

        assertThat(response.heartRateSource()).isEqualTo(HeartRateSource.WATCH);
        assertThat(response.syncStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(response.avgBpm()).isEqualTo(152);
        assertThat(response.maxBpm()).isEqualTo(168);
        assertThat(response.hrvMs()).isEqualTo(42);
    }

    @Test
    void 업로드한_syncedAt이_측정_시각으로_저장된다() {
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 4, 6, 55);

        UUID measurementId = heartRateMeasurementService.uploadWatchMeasurement(
                userId, new AppleHealthDto.HeartRateRequest(
                        session.getRunningSessionId(), 152, 168, 42, syncedAt))
                .heartRateMeasurementId();

        HeartRateMeasurement saved = heartRateMeasurementRepository.findById(measurementId).orElseThrow();
        assertThat(saved.getMeasuredAt()).isEqualTo(syncedAt);
        assertThat(saved.getSignalQuality()).isNull();
    }

    @Test
    void HRV가_없어도_업로드할_수_있다() {
        HeartRateMeasurementResponse response = heartRateMeasurementService.uploadWatchMeasurement(
                userId, new AppleHealthDto.HeartRateRequest(
                        session.getRunningSessionId(), 152, 168, null, LocalDateTime.now()));

        assertThat(response.hrvMs()).isNull();
        assertThat(response.syncStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void 남의_세션에_워치_데이터를_업로드하면_E4030이다() {
        UUID otherUserId = userRepository.save(User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed").nickname("남").build()).getUserId();

        assertThatThrownBy(() -> heartRateMeasurementService.uploadWatchMeasurement(
                otherUserId, new AppleHealthDto.HeartRateRequest(
                        session.getRunningSessionId(), 152, 168, 42, LocalDateTime.now())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // --- 4.3 연동 기록 ---

    @Test
    void 연동_정보가_없던_사용자도_연동을_기록할_수_있다() {
        AppleHealthDto.LinkResponse response = heartRateMeasurementService.linkAppleHealth(
                userId, new AppleHealthDto.LinkRequest(true));

        assertThat(response.appleHealthLinked()).isTrue();
        assertThat(integrationStatusRepository.findById(userId))
                .get().extracting(IntegrationStatus::isAppleHealthLinked).isEqualTo(true);
    }

    @Test
    void 연동을_해제하면_false로_갱신된다() {
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(true));

        AppleHealthDto.LinkResponse response = heartRateMeasurementService.linkAppleHealth(
                userId, new AppleHealthDto.LinkRequest(false));

        assertThat(response.appleHealthLinked()).isFalse();
    }

    @Test
    void 연동을_두_번_기록해도_행이_하나만_남는다() {
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(true));
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(true));

        assertThat(integrationStatusRepository.findById(userId)).isPresent();
    }

    // --- 기본 측정 방식 파생 ---

    @Test
    void 측정_이력이_있으면_가장_최근_방식이_기본이다() {
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, 152, 168, 42, LocalDateTime.now().minusDays(3)));
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 146, 158, 38, LocalDateTime.now().minusDays(1), SignalQuality.GOOD));

        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.RPPG);
    }

    /** 실패한 측정도 "그 방식을 골랐다"는 사실은 남는다. */
    @Test
    void 최근_측정이_실패했어도_그_방식이_기본이다() {
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, 152, 168, 42, LocalDateTime.now().minusDays(3)));
        heartRateMeasurementRepository.save(HeartRateMeasurement.rppg(
                session, 146, 158, 38, LocalDateTime.now().minusDays(1), SignalQuality.POOR));

        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.RPPG);
    }

    @Test
    void 이력이_없고_애플_헬스가_연동됐으면_워치가_기본이다() {
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(true));

        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.WATCH);
    }

    @Test
    void 이력이_없고_애플_헬스가_연동되지_않았으면_rPPG가_기본이다() {
        heartRateMeasurementService.linkAppleHealth(userId, new AppleHealthDto.LinkRequest(false));

        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.RPPG);
    }

    /** 가입 직후라 integration_status 행 자체가 없는 사용자. */
    @Test
    void 연동_정보가_아예_없으면_rPPG가_기본이다() {
        assertThat(heartRateMeasurementService.defaultSourceFor(userId))
                .isEqualTo(HeartRateSource.RPPG);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
docker compose ps
./gradlew test --tests '*HeartRateAppleHealthTest'
```

Expected: 컴파일 실패 — `selectSource`, `uploadWatchMeasurement`, `linkAppleHealth`, `defaultSourceFor` 메서드 없음.

- [ ] **Step 3: 4.1 / 4.2 / 4.3과 기본 source 파생을 구현한다**

`HeartRateMeasurementService`에 추가한다.

```java
    /**
     * R4.1 POST /running-sessions/{id}/heart-rate/select-source
     * <p>선택값을 저장하지 않는다 — 이후 흐름(R4.2/R4.6)이 각자 source를 확정하므로 읽는 곳이 없다.
     * 세션 소유자만 확인하고 다음 화면을 알려준다.
     */
    public SelectSourceDto.Response selectSource(UUID userId,
                                                 UUID sessionId,
                                                 SelectSourceDto.Request request) {
        getOwnedSession(userId, sessionId);

        String nextStep = request.heartRateSource() == HeartRateSource.WATCH
                ? SelectSourceDto.Response.NEXT_STEP_FETCH_APPLE_HEALTH
                : SelectSourceDto.Response.NEXT_STEP_RPPG_GUIDE;

        return new SelectSourceDto.Response(request.heartRateSource(), nextStep);
    }

    /**
     * R4.2 POST /integrations/apple-health/heart-rate
     * <p>명세는 서버가 애플 헬스를 조회하는 GET이지만, HealthKit은 온디바이스 API라 서버가 읽을 수 없다.
     * 앱이 읽은 값을 올리는 구조로 바꿨다. 앱은 읽기에 성공했을 때만 호출하므로 항상 SUCCESS다.
     */
    @Transactional
    public HeartRateMeasurementResponse uploadWatchMeasurement(UUID userId,
                                                               AppleHealthDto.HeartRateRequest request) {
        RunningSession session = getOwnedSession(userId, request.runningSessionId());

        HeartRateMeasurement measurement = heartRateMeasurementRepository.save(
                HeartRateMeasurement.watch(
                        session,
                        request.avgBpm(),
                        request.maxBpm(),
                        request.hrvMs(),
                        request.syncedAt()
                ));

        return HeartRateMeasurementResponse.from(measurement);
    }

    /**
     * R4.3 POST /integrations/apple-health/link
     * <p>명세의 authorize를 대체한다 — HealthKit 권한 동의는 OS 다이얼로그로 끝나므로
     * 서버가 돌려줄 authorizeUrl이 없다. 앱이 동의 결과를 알려오면 기록만 한다.
     * <p>사용자가 iOS 설정에서 권한을 회수하면 false로도 들어온다.
     */
    @Transactional
    public AppleHealthDto.LinkResponse linkAppleHealth(UUID userId, AppleHealthDto.LinkRequest request) {
        IntegrationStatus status = integrationStatusRepository.findById(userId)
                .orElseGet(() -> IntegrationStatus.of(userId));

        status.linkAppleHealth(request.linked());
        integrationStatusRepository.save(status);

        return new AppleHealthDto.LinkResponse(status.isAppleHealthLinked());
    }

    /**
     * 화면 5에서 기본으로 선택해 둘 측정 방식.
     * <p>명세에 없는 요구(최근 쓴 방식이 기본, 버튼으로 전환)를 위해 R3.5 /end 응답이 실어 보낸다.
     * 별도 컬럼 없이 측정 이력에서 파생하므로, 고르기만 하고 측정을 끝내지 않은 선택은 기억되지 않는다.
     */
    public HeartRateSource defaultSourceFor(UUID userId) {
        return heartRateMeasurementRepository
                .findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(userId)
                .map(HeartRateMeasurement::getHeartRateSource)
                .orElseGet(() -> integrationStatusRepository.findById(userId)
                        .filter(IntegrationStatus::isAppleHealthLinked)
                        .map(status -> HeartRateSource.WATCH)
                        .orElse(HeartRateSource.RPPG));
    }
```

import에 `AppleHealthDto`, `SelectSourceDto`, `HeartRateSource`, `IntegrationStatus`를 넣는다.

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*HeartRateAppleHealthTest'
```

Expected: PASS (16개).

- [ ] **Step 5: 서비스 전체 테스트로 회귀를 확인한다**

```bash
./gradlew test --tests '*HeartRate*'
```

Expected: 모두 PASS.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/heartrate/service/HeartRateMeasurementService.java \
        src/test/java/jungkathon3team/aftergrow/heartrate/service/HeartRateAppleHealthTest.java
git commit -m "feat: 애플 헬스 연동 및 측정 방식 선택 구현"
```

---

## Task 9: 컨트롤러 2개와 4.1 엔드포인트

서비스 로직이 전부 준비됐으므로 HTTP 표면을 붙인다.

**Files:**
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/controller/HeartRateMeasurementController.java`
- Create: `src/main/java/jungkathon3team/aftergrow/heartrate/controller/AppleHealthController.java`
- Modify: `src/main/java/jungkathon3team/aftergrow/running/controller/RunningSessionController.java`
- Test: `src/test/java/jungkathon3team/aftergrow/heartrate/controller/HeartRateControllerTest.java` (신규)

**Interfaces:**
- Consumes: `HeartRateMeasurementService`의 8개 public 메서드 (Task 5~8), 모든 DTO (Task 4)
- Produces: HTTP 엔드포인트 8개. 응답은 전부 `ApiResponse<T>`로 감싼다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

인가 규칙과 응답 래핑만 확인한다. 값 계산은 이미 서비스 테스트가 덮었다.

`src/test/java/jungkathon3team/aftergrow/heartrate/controller/HeartRateControllerTest.java`:

```java
package jungkathon3team.aftergrow.heartrate.controller;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** R4/R6 엔드포인트의 인가와 응답 래핑을 고정한다. 값 계산은 서비스 테스트가 덮는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HeartRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String bearer;
    private RunningSession session;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("ctrl-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    @Test
    void rPPG_안내는_ApiResponse로_감싸서_반환한다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements/rppg/guide").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.durationSec").value(12))
                .andExpect(jsonPath("$.data.instruction").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void 측정_기록_목록은_range를_생략해도_조회된다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.sourceRatio.watch").value(0));
    }

    @Test
    void 잘못된_range는_400과_E4001이다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements").param("range", "abc")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void 측정_방식_선택은_다음_단계를_알려준다() throws Exception {
        mockMvc.perform(post("/running-sessions/{id}/heart-rate/select-source",
                        session.getRunningSessionId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heartRateSource\":\"RPPG\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("RPPG_GUIDE"));
    }

    @Test
    void rPPG_측정_시작은_201이다() throws Exception {
        mockMvc.perform(post("/heart-rate-measurements/rppg/start")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runningSessionId\":\"" + session.getRunningSessionId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MEASURING"));
    }

    @Test
    void 워치_데이터_업로드는_201이다() throws Exception {
        mockMvc.perform(post("/integrations/apple-health/heart-rate")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runningSessionId": "%s",
                                  "avgBpm": 152,
                                  "maxBpm": 168,
                                  "hrvMs": 42,
                                  "syncedAt": "2026-08-04T06:55:00"
                                }
                                """.formatted(session.getRunningSessionId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.syncStatus").value("SUCCESS"));
    }

    @Test
    void 애플_헬스_연동_기록은_200이다() throws Exception {
        mockMvc.perform(post("/integrations/apple-health/link")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appleHealthLinked").value(true));
    }

    @Test
    void 요청_본문_검증에_실패하면_400과_E4001이다() throws Exception {
        mockMvc.perform(post("/heart-rate-measurements/rppg/start")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    // --- 인가 ---

    @Test
    void 토큰이_없으면_측정_기록을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("E4010"));
    }

    @Test
    void 토큰이_없으면_rPPG_안내도_볼_수_없다() throws Exception {
        mockMvc.perform(get("/heart-rate-measurements/rppg/guide"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰이_없으면_애플_헬스_연동을_기록할_수_없다() throws Exception {
        mockMvc.perform(post("/integrations/apple-health/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linked\":true}"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
docker compose ps
./gradlew test --tests '*HeartRateControllerTest'
```

Expected: 404 다수 — 엔드포인트가 아직 없음.

- [ ] **Step 3: `HeartRateMeasurementController`를 만든다**

`src/main/java/jungkathon3team/aftergrow/heartrate/controller/HeartRateMeasurementController.java`:

```java
package jungkathon3team.aftergrow.heartrate.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateMeasurementResponse;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateRecordsResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgGuideResponse;
import jungkathon3team.aftergrow.heartrate.dto.RppgResultDto;
import jungkathon3team.aftergrow.heartrate.dto.RppgStartDto;
import jungkathon3team.aftergrow.heartrate.dto.RetryResponse;
import jungkathon3team.aftergrow.heartrate.service.HeartRateMeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "심박수", description = "rPPG 측정 / 측정 기록")

@RestController
@RequestMapping("/heart-rate-measurements")
@RequiredArgsConstructor
public class HeartRateMeasurementController {

    private final HeartRateMeasurementService heartRateMeasurementService;

    @Operation(summary = "rPPG 측정 안내",
            description = "화면 6 진입 시 측정 방법과 소요 시간을 안내합니다.")
    @GetMapping("/rppg/guide")
    public ApiResponse<RppgGuideResponse> rppgGuide() {
        return ApiResponse.ok(heartRateMeasurementService.rppgGuide());
    }

    @Operation(summary = "rPPG 측정 시작",
            description = "측정 세션을 발급합니다. 결과 제출 전까지 측정 기록은 생성되지 않습니다.")
    @PostMapping("/rppg/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RppgStartDto.Response> startRppg(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody RppgStartDto.Request request
    ) {
        return ApiResponse.ok(heartRateMeasurementService.startRppg(userId, request));
    }

    @Operation(summary = "rPPG 측정 결과 제출",
            description = "카메라 원본 영상이 아니라 온디바이스 알고리즘의 결과값만 받습니다. "
                    + "신호 품질이 POOR이면 재측정이 필요한 기록으로 저장됩니다.")
    @PostMapping("/rppg/{rppgSessionId}/result")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HeartRateMeasurementResponse> submitRppgResult(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID rppgSessionId,
            @Valid @RequestBody RppgResultDto.Request request
    ) {
        return ApiResponse.ok(
                heartRateMeasurementService.submitRppgResult(userId, rppgSessionId, request));
    }

    @Operation(summary = "측정 기록 목록",
            description = "range는 \"{일수}d\" 형식입니다(기본 30d).")
    @GetMapping
    public ApiResponse<HeartRateRecordsResponse> getRecords(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String range
    ) {
        return ApiResponse.ok(heartRateMeasurementService.getRecords(userId, range));
    }

    @Operation(summary = "실패 기록 재측정",
            description = "실패 기록은 삭제하지 않고, rPPG 측정 흐름으로 되돌아갈 정보를 반환합니다.")
    @PostMapping("/{id}/retry")
    public ApiResponse<RetryResponse> retry(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID measurementId
    ) {
        return ApiResponse.ok(heartRateMeasurementService.retry(userId, measurementId));
    }
}
```

- [ ] **Step 4: `AppleHealthController`를 만든다**

`src/main/java/jungkathon3team/aftergrow/heartrate/controller/AppleHealthController.java`:

```java
package jungkathon3team.aftergrow.heartrate.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.heartrate.dto.AppleHealthDto;
import jungkathon3team.aftergrow.heartrate.dto.HeartRateMeasurementResponse;
import jungkathon3team.aftergrow.heartrate.service.HeartRateMeasurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 애플 헬스(HealthKit) 연동.
 * <p>HealthKit은 온디바이스 API라 서버가 직접 읽거나 권한을 요청할 수 없다.
 * 명세의 GET 두 개를, 앱이 읽은 값과 동의 결과를 올리는 POST로 바꿨다.
 */
@Tag(name = "애플 헬스 연동", description = "워치 심박수 업로드 / 연동 상태 기록")

@RestController
@RequestMapping("/integrations/apple-health")
@RequiredArgsConstructor
public class AppleHealthController {

    private final HeartRateMeasurementService heartRateMeasurementService;

    @Operation(summary = "워치 심박수 업로드",
            description = "앱이 HealthKit에서 읽은 측정값을 러닝 세션에 기록합니다.")
    @PostMapping("/heart-rate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HeartRateMeasurementResponse> uploadHeartRate(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AppleHealthDto.HeartRateRequest request
    ) {
        return ApiResponse.ok(heartRateMeasurementService.uploadWatchMeasurement(userId, request));
    }

    @Operation(summary = "애플 헬스 연동 기록",
            description = "HealthKit 권한 동의 결과를 기록합니다. 권한 회수 시 false로도 호출됩니다.")
    @PostMapping("/link")
    public ApiResponse<AppleHealthDto.LinkResponse> link(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AppleHealthDto.LinkRequest request
    ) {
        return ApiResponse.ok(heartRateMeasurementService.linkAppleHealth(userId, request));
    }
}
```

- [ ] **Step 5: `RunningSessionController`에 4.1을 추가한다**

경로가 `/running-sessions`로 시작해 기존 클래스 매핑에 그대로 들어맞는다. 새 컨트롤러를 만들지 않는다.

필드에 서비스를 추가한다:

```java
    private final RunningSessionService runningSessionService;
    private final HeartRateMeasurementService heartRateMeasurementService;
```

`end` 메서드 아래에 추가한다:

```java
    @Operation(summary = "심박수 측정 방식 선택",
            description = "화면 5에서 워치/rPPG 중 하나를 고르면 다음 화면을 알려줍니다. "
                    + "선택값은 저장하지 않습니다.")
    @PostMapping("/{id}/heart-rate/select-source")
    public ApiResponse<SelectSourceDto.Response> selectHeartRateSource(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody SelectSourceDto.Request request
    ) {
        return ApiResponse.ok(
                heartRateMeasurementService.selectSource(userId, sessionId, request));
    }
```

import에 `jungkathon3team.aftergrow.heartrate.dto.SelectSourceDto`와 `jungkathon3team.aftergrow.heartrate.service.HeartRateMeasurementService`를 넣는다.

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*HeartRateControllerTest'
```

Expected: PASS (11개).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/heartrate/controller/ \
        src/main/java/jungkathon3team/aftergrow/running/controller/RunningSessionController.java \
        src/test/java/jungkathon3team/aftergrow/heartrate/controller/
git commit -m "feat: 심박수 측정·기록 API 엔드포인트 추가"
```

---

## Task 10: 러닝 종료 응답에 기본 측정 방식 추가

명세에 없는 신규 요구다. 화면 5 상단의 두 선택지 중 **최근에 쓴 방식이 기본으로 선택된 상태**로 뜨게 하려면, 화면 진입 시점에 서버가 기본값을 알려줘야 한다.

새 엔드포인트를 만들지 않고 3.5 `/end` 응답에 얹는다 — 러닝 종료 → 화면 5 진입이 유일한 경로다.

**Files:**
- Modify: `src/main/java/jungkathon3team/aftergrow/running/dto/RunningEndDto.java`
- Modify: `src/main/java/jungkathon3team/aftergrow/running/service/RunningSessionService.java`
- Test: `src/test/java/jungkathon3team/aftergrow/running/RunningEndDefaultSourceTest.java` (신규)

**Interfaces:**
- Consumes: `HeartRateMeasurementService.defaultSourceFor(UUID userId)` (Task 8)
- Produces: `RunningEndDto.Response.defaultHeartRateSource` (`HeartRateSource`)

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/jungkathon3team/aftergrow/running/RunningEndDefaultSourceTest.java`:

```java
package jungkathon3team.aftergrow.running;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 러닝 종료 응답이 화면 5의 기본 측정 방식을 함께 내려주는지 확인한다.
 * <p>명세에 없는 신규 요구라, 별도 엔드포인트 없이 /end 응답에 얹었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RunningEndDefaultSourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningSessionRepository runningSessionRepository;

    @Autowired
    private HeartRateMeasurementRepository heartRateMeasurementRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String bearer;
    private RunningSession session;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("end-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
        session = runningSessionRepository.save(
                RunningSession.start(user, LocalDateTime.now().minusHours(1), 37.5, 127.0, 5));
    }

    private org.springframework.test.web.servlet.ResultActions endRunning() throws Exception {
        return mockMvc.perform(post("/running-sessions/{id}/end", session.getRunningSessionId())
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "endedAt": "2026-08-10T08:00:00",
                          "durationSec": 1800,
                          "distanceKm": 4.8,
                          "intensity": "HIGH"
                        }
                        """));
    }

    @Test
    void 측정_이력이_없으면_rPPG가_기본이다() throws Exception {
        endRunning()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextStep").value("HEART_RATE_CHECK"))
                .andExpect(jsonPath("$.data.defaultHeartRateSource").value("RPPG"));
    }

    @Test
    void 최근_측정이_워치면_워치가_기본이다() throws Exception {
        heartRateMeasurementRepository.save(HeartRateMeasurement.watch(
                session, 152, 168, 42, LocalDateTime.now().minusDays(1)));

        endRunning()
                .andExpect(jsonPath("$.data.defaultHeartRateSource").value("WATCH"));
    }

    /** 이미 끝난 세션에 다시 호출해도 에러 없이 같은 응답이어야 한다(멱등). */
    @Test
    void 두_번_종료해도_기본_측정_방식이_함께_온다() throws Exception {
        endRunning().andExpect(status().isOk());

        endRunning()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.defaultHeartRateSource").value("RPPG"));
    }
}
```

`Intensity`는 `LOW` / `MODERATE` / `HIGH`이므로 위 본문의 `"HIGH"`가 그대로 유효하다 (확인 완료).

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
docker compose ps
./gradlew test --tests '*RunningEndDefaultSourceTest'
```

Expected: `defaultHeartRateSource` 경로가 없어 실패.

- [ ] **Step 3: `RunningEndDto.Response`에 필드를 추가한다**

```java
    public record Response(
            UUID runningSessionId,
            RunningStatus status,
            String nextStep, // 항상 "HEART_RATE_CHECK" (화면 5로 이동)
            // 화면 5에서 기본으로 선택해 둘 측정 방식. 명세에 없는 추가 항목이며,
            // 최근 측정 이력에서 파생한다(별도 컬럼 없음).
            HeartRateSource defaultHeartRateSource
    ) {
        public static final String NEXT_STEP_HEART_RATE_CHECK = "HEART_RATE_CHECK";
    }
```

import에 `jungkathon3team.aftergrow.heartrate.entity.HeartRateSource`를 넣는다.

- [ ] **Step 4: `RunningSessionService`가 기본 방식을 채우게 한다**

필드에 서비스를 추가한다:

```java
    private final HeartRateMeasurementService heartRateMeasurementService;
```

`endRunning`의 반환부를 교체한다:

```java
        return new RunningEndDto.Response(
                session.getRunningSessionId(),
                session.getStatus(),
                RunningEndDto.Response.NEXT_STEP_HEART_RATE_CHECK,
                // 화면 5의 기본 선택지. 러닝 종료 → 화면 5 진입이 유일한 경로라 여기서 함께 내려준다.
                heartRateMeasurementService.defaultSourceFor(userId)
        );
```

import에 `jungkathon3team.aftergrow.heartrate.service.HeartRateMeasurementService`를 넣는다.

순환 의존은 생기지 않는다 — `HeartRateMeasurementService`는 `RunningSessionRepository`에만 의존하고 `RunningSessionService`를 부르지 않는다.

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*RunningEndDefaultSourceTest'
```

Expected: PASS (3개).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/jungkathon3team/aftergrow/running/dto/RunningEndDto.java \
        src/main/java/jungkathon3team/aftergrow/running/service/RunningSessionService.java \
        src/test/java/jungkathon3team/aftergrow/running/
git commit -m "feat: 러닝 종료 응답에 기본 심박수 측정 방식 추가"
```

---

## Task 11: API 명세 문서 반영과 최종 검증

구현이 명세와 어긋난 채로 남으면 프론트엔드가 잘못된 계약을 보고 만든다. 변경점을 `docs/API_명세.md`에 반영한다.

**Files:**
- Modify: `docs/API_명세.md`
- Test: 없음 (전체 테스트 실행으로 검증)

**Interfaces:**
- Consumes: Task 1~10의 최종 계약
- Produces: 갱신된 API 명세

- [ ] **Step 1: R4.2를 POST로 고친다**

`docs/API_명세.md`의 §4.2를 아래로 교체한다.

````markdown
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
````

기존 §4.2의 `E5010` 실패 응답 블록을 삭제하고, 그 자리에 아래를 넣는다.

```markdown
> 서버가 애플 헬스를 호출하지 않게 되어 `E5010`은 이 엔드포인트에서 발생하지 않습니다.
> 공통 에러 코드 표(§0)에는 그대로 남아 있습니다.
```

- [ ] **Step 2: R4.3을 link로 고친다**

§4.3을 아래로 교체한다.

````markdown
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
````

- [ ] **Step 3: 4.6 실패 응답과 6.1 sourceRatio 정의를 명확히 한다**

§4.6 마지막 줄을 교체한다.

```markdown
- `signalQuality: "POOR"`인 경우 서버는 `syncStatus: "FAILED"`로 저장하고 **`avgBpm`/`maxBpm`/`hrvMs`를 `null`로 버립니다**
  (신뢰할 수 없는 값이 홈 대시보드의 주간 평균 bpm에 섞이지 않도록). 기록 화면의 "측정 실패 · 재측정 필요"에 대응하며,
  실패도 에러가 아니라 재측정이 필요한 기록이므로 응답은 201입니다.
```

§6.1의 응답 예시 아래에 추가한다.

```markdown
- `range`는 `{일수}d` 형식입니다(`7d`, `30d`, `90d`…). 생략하면 `30d`. 형식이 어긋나거나 0 이하면 `E4001`입니다.
- `sourceRatio`는 같은 `range` 안의 실제 건수입니다. `rppgFailedCount`는 `rppg`에서 빠지는 값이 아니라 **부분집합**입니다.
```

- [ ] **Step 4: 3.5 응답에 기본 측정 방식을 반영한다**

§3.5(러닝 종료)의 Response 예시에 `defaultHeartRateSource`를 추가하고 설명을 붙인다. 실제 필드명·기존 예시 형태는 문서를 열어 확인한 뒤 맞춘다.

```markdown
- `defaultHeartRateSource`는 화면 5에서 기본으로 선택해 둘 측정 방식입니다.
  가장 최근 측정의 방식을 쓰고, 측정 이력이 없으면 애플 헬스 연동 여부에 따라 `WATCH`/`RPPG`입니다.
  사용자는 화면 5 상단의 버튼으로 다른 방식을 고를 수 있습니다.
```

- [ ] **Step 5: 전체 테스트를 돌린다**

```bash
docker compose ps
./gradlew test
```

Expected: 기존 51개 + 신규 전부 PASS. 실패하면 여기서 멈추고 원인을 고친다.

- [ ] **Step 6: CI 환경(test 프로파일)을 재현한다**

프로파일별로 설정이 달라 한쪽만 통과하는 일이 있다. 비밀번호는 `.env` 값으로 바꾼다.

```bash
SPRING_PROFILES_ACTIVE=test \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/aftergrow_test \
SPRING_DATASOURCE_USERNAME=dev \
SPRING_DATASOURCE_PASSWORD=... \
SPRING_DATA_REDIS_HOST=localhost \
./gradlew cleanTest test
```

Expected: PASS. 설정 키를 추가하지 않았으므로 `PlaceholderResolutionException`이 나면 안 된다.

- [ ] **Step 7: 빌드를 확인한다**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: `gradlew` 권한 비트를 확인한다**

Windows는 `core.fileMode=false`라 권한 비트를 무시한다. `100644`가 되면 로컬은 멀쩡하고 Ubuntu 러너에서만 `Permission denied`(exit 126)로 죽는다.

```bash
git ls-files -s gradlew
```

Expected: `100755 ... gradlew`. `100644`면 `git update-index --chmod=+x gradlew`로 고치고 커밋한다.

- [ ] **Step 9: 커밋**

```bash
git add docs/API_명세.md
git commit -m "docs: R4 심박수 측정 API 변경점 명세 반영"
```

---

## Self-Review 결과

이 계획을 작성한 뒤 설계 문서와 대조한 기록이다.

**스펙 커버리지 — 전부 태스크에 있음**

| 설계 문서 | 태스크 |
|---|---|
| §2.1 GET→POST | Task 8 (`uploadWatchMeasurement`), Task 9 (`AppleHealthController`), Task 11 (문서) |
| §2.2 authorize→link | Task 2, Task 8 (`linkAppleHealth`), Task 11 |
| §2.3 E5010 유지 | Global Constraints (삭제 금지), Task 11 |
| §2.4 기본 source 파생 | Task 8 (`defaultSourceFor`), Task 10 (`/end` 응답) |
| §2.5 sourceRatio 정의 | Task 6 |
| §3 엔드포인트 9개 | Task 9 (8개) + Task 10 (3.5 수정) |
| §4.1 enum 확정 | Task 1 |
| §4.2 Redis rPPG 세션 | Task 3, Task 7 |
| §4.3 POOR → null | Task 1 (엔티티), Task 7 (흐름 검증) |
| §4.4 마이그레이션 없음 | Global Constraints |
| §5 패키지 구조 | File Structure |
| §5.1 엔티티 규약 | Task 1, Task 2 |
| §5.2 리포지토리 쿼리 | Task 5 |
| §6 에러 처리 | Task 7 (`getOwnedSession`), Task 5 (`sinceOf`) |
| §7 테스트 4가지 | Task 1(POOR), Task 5(range), Task 6(sourceRatio), Task 8(기본 source) |
| §8 구현 순서 | Task 1~11 |
| §9 검증 | Task 11 |

**설계 문서에서 의도적으로 벗어난 것 1건:** §5.1의 `RunningSessionService` 의존 방향. 리포지토리 2개 대신 `HeartRateMeasurementService` 1개를 주입한다 (File Structure 절에 근거 기재).

**설계 문서 §7이 "한 파일"이라 했으나 4개로 나뉜 것:** Redis를 쓰는 rPPG 흐름은 `@Transactional` 롤백이 통하지 않아 `@AfterEach`로 키를 지워야 하고, 순수 단위 테스트(엔티티)는 Spring 컨텍스트 없이 도는 편이 빠르다. 테스트 대상은 §7의 네 가지를 모두 덮는다.

**타입 일관성 확인** — 태스크 간 이름이 어긋나지 않는지 대조함:

- `HeartRateMeasurement.watch(...)` / `.rppg(...)` — Task 1 정의 → Task 6·7·8·10 사용, 인자 순서 동일
- `HeartRateMeasurementResponse.from(...)` — Task 4 정의 → Task 7·8 사용
- `HeartRateRecordsResponse.Item` (`Record` 아님) — Task 4 정의 → Task 6 사용
- `RppgGuideResponse.DURATION_SEC` — Task 4 정의 → Task 7 (`startRppg`)에서 재사용, 4.4/4.5 값이 갈리지 않음
- `sinceOf(String, LocalDateTime)` — Task 5 정의 → Task 6 사용
- `getOwnedSession(UUID, UUID)` — Task 7 정의 → Task 8 (`selectSource`, `uploadWatchMeasurement`) 사용. **Task 8을 Task 7보다 먼저 하면 컴파일이 깨진다.**
- `defaultSourceFor(UUID)` — Task 8 정의 → Task 10 사용
- `IntegrationStatus.isAppleHealthLinked()` — Task 2 정의(Lombok `@Getter` + `boolean`) → Task 8 사용

**태스크 순서 제약:** 1 → 2 → 3 → 4 → 5 → 6 → **7 → 8** → 9 → 10 → 11. Task 7과 8의 순서를 바꾸면 `getOwnedSession`이 없어 컴파일이 깨진다.

**계획 작성 중 코드로 확인을 마친 것:**

- `BusinessException`은 `private final ErrorCode errorCode` + Lombok `@Getter` → `extracting("errorCode")` 유효
- `Intensity` = `LOW` / `MODERATE` / `HIGH` → Task 10의 `"HIGH"` 유효

**구현자가 문서를 열어 맞춰야 하는 것 1개:** `docs/API_명세.md` §3.5의 기존 응답 예시 형태 (Task 11 Step 4에서 `defaultHeartRateSource`를 끼워 넣을 때).
