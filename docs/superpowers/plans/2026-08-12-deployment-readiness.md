# 배포 산출물 레포화 · actuator 헬스체크 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** EC2에만 존재하던 배포 산출물(Dockerfile, compose, prod 설정)을 레포로 옮기고, `/actuator/health`를 인증 없이 열어 헬스체크가 동작하게 한다.

**Architecture:** 이미지 빌드는 GitHub Actions 러너에서만 수행하고 GHCR에 올린 뒤, 같은 워크플로가 SSH로 EC2에 들어가 `pull` + `up -d` + 헬스체크까지 수행한다. EC2(t2.micro, 1GB)는 빌드하지 않는다 — 1GB 호스트에서 Gradle 빌드를 돌리면 같은 호스트의 postgres·app이 OOM으로 종료될 수 있기 때문이다. 로컬 개발용 `docker-compose.yml`은 건드리지 않고 배포용 `docker-compose.prod.yml`을 따로 둔다.

**Tech Stack:** Java 17, Spring Boot 4.0.7, Spring Security 7.0.6, Gradle 9.5.1 (wrapper), Docker / Docker Compose, GitHub Actions, GHCR.

**설계 문서:** `docs/superpowers/specs/2026-08-12-deployment-readiness-design.md`

## Global Constraints

- **패키지명은 `aftergrow`다. `afterglow`가 아니다.**
- **Java 17 고정.** Dockerfile의 베이스 이미지와 `build.gradle` toolchain 둘 다 17이어야 한다.
- **Spring Boot 4 import를 쓴다.** MockMvc 자동설정은 `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (Boot 3의 `...boot.test.autoconfigure.web.servlet...`이 아니다).
- **새 마이그레이션 파일을 만들지 않는다.** 스키마는 변경되지 않는다.
- **`PUBLIC_PATHS`에 와일드카드를 쓰지 않는다.** `/actuator/health`만 정확히 추가한다. `/actuator/**`로 열면 이후 노출되는 actuator 엔드포인트가 자동으로 공개된다.
- **`application.yml`을 수정하지 않는다.** `spring.profiles.active: local` 하드코딩은 그대로 둔다 — 환경변수가 설정 파일보다 우선순위가 높아 `SPRING_PROFILES_ACTIVE=prod`가 덮어쓴다. CI가 이미 같은 방식으로 동작 중이다.
- **비밀값을 커밋하지 않는다.** `.env`, `application-local.yml`은 gitignore 대상이다. `docker-compose.prod.yml`에는 `${POSTGRES_PASSWORD}` 형태의 참조만 쓴다.
- **설정 키를 추가하면 이제 세 파일을 확인한다** — `application-local.yml`(gitignore), `src/test/resources/application-test.yml`, `src/main/resources/application-prod.yml`.
- 커밋 메시지: `feat|fix|refactor|test|docs|chore: 한국어 설명`
- 브랜치: `feature/deploy-pipeline` (**이미 생성되어 있고 설계 문서 커밋 `22521da`가 올라가 있다**). PR 대상은 `main`.

## 사전 조건

**Task 1의 테스트 실행 전에 Docker 컨테이너가 떠 있어야 한다.** `@SpringBootTest`가 실제 PostgreSQL/Redis에 붙는다.

```bash
docker compose up -d
docker compose ps          # postgres/redis 가 healthy 인지 확인
```

컨테이너 없이 돌리면 HibernateException으로 실패한다. 테스트가 깨지면 이것부터 확인할 것.

Task 2 이후는 `docker` CLI가 필요하다.

---

## File Structure

### 신규 생성

| 파일 | 책임 |
|---|---|
| `Dockerfile` | 멀티스테이지 이미지 정의. 빌드 스테이지는 Actions 러너에서만 실행된다 |
| `.dockerignore` | 빌드 컨텍스트에서 `build/`, `.gradle/`, `.git/` 제외 |
| `src/main/resources/application-prod.yml` | 커밋되는 prod 설정. 비밀값은 `${}` 참조만 |
| `docker-compose.prod.yml` | EC2용 전체 스택(app + postgres + redis) |
| `.github/workflows/deploy.yml` | 이미지 빌드 → GHCR 푸시 |

### 수정

| 파일 | 변경 내용 |
|---|---|
| `build.gradle` | `spring-boot-starter-actuator` 의존성 추가 |
| `src/main/java/jungkathon3team/aftergrow/common/config/SecurityConfig.java` | `PUBLIC_PATHS`에 `/actuator/health` 추가 |
| `src/test/java/jungkathon3team/aftergrow/common/config/SecurityConfigTest.java` | actuator 헬스체크 테스트 추가 |
| `.env.example` | `REDIS_HOST`가 로컬 전용임을 주석으로 명시 (prod 키는 이미 전부 있음) |
| `CLAUDE.md` | 설정 파일 3곳 규칙, 배포 절차, actuator |

### 건드리지 않는 파일

- `docker-compose.yml` — 로컬 개발용으로 그대로 둔다
- `application.yml` — Global Constraints 참고
- `.github/workflows/test.yml` — `main` 대상 PR에서 돌고, `deploy.yml`은 `main` push에서 돈다. 트리거가 겹치지 않는다
- `.gitignore` — override 체인을 쓰지 않으므로 `docker-compose.override.yml` 줄은 무해하다

---

## Task 1: `/actuator/health`를 인증 없이 연다

설계 문서 §6에 해당한다. 의존성과 `PUBLIC_PATHS`는 한 변경의 두 절반이라 한 커밋으로 묶는다 — 의존성만 넣으면 401, `PUBLIC_PATHS`만 넣으면 404다.

**Files:**
- Modify: `build.gradle:20-44` (dependencies 블록)
- Modify: `src/main/java/jungkathon3team/aftergrow/common/config/SecurityConfig.java:34-41` (`PUBLIC_PATHS`)
- Test: `src/test/java/jungkathon3team/aftergrow/common/config/SecurityConfigTest.java` (기존 파일에 메서드 추가)

**Interfaces:**
- Consumes: 없음 (첫 번째 태스크)
- Produces: `GET /actuator/health` — 인증 없이 200, 본문 `{"status":"UP"}`. Task 4의 EC2 검증 절차와 설계 문서 §11이 이 엔드포인트를 쓴다.

**새 테스트 파일을 만들지 않는다.** `SecurityConfigTest`가 이미 "어떤 경로가 인증 없이 열리는가"를 검증하는 파일이고 (`API_문서는_인증_없이_열린다`), actuator도 같은 질문이다.

- [ ] **Step 1: 실패하는 테스트를 작성한다**

`src/test/java/jungkathon3team/aftergrow/common/config/SecurityConfigTest.java`의 마지막 `}` 앞에 아래 메서드를 추가한다. import는 이미 파일에 전부 있다 (`get`, `jsonPath`, `status`).

```java
    /**
     * 헬스체크는 로드밸런서·모니터링이 토큰 없이 호출한다.
     * <p>이 테스트 하나가 두 가지를 동시에 지킨다 — actuator 의존성이 없으면 404,
     * PUBLIC_PATHS에 없으면 401이라 어느 쪽이 빠져도 실패한다.
     * <p>검증이 {@code $.data.…}가 아니라 {@code $.status}인 이유: actuator 응답은
     * ApiResponse 래퍼를 거치지 않는다. 이 레포에서 유일한 예외다.
     */
    @Test
    void 헬스체크는_인증_없이_열린다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```bash
docker compose up -d
./gradlew test --tests '*SecurityConfigTest'
```

Expected: FAIL. `헬스체크는_인증_없이_열린다`가 `Status expected:<200> but was:<401>`로 실패한다. 나머지 두 테스트는 통과한다.

**404가 아니라 401이다.** `AuthorizationFilter`가 `DispatcherServlet`보다 앞에 있어서, 공개 경로가 아닌 URL은 핸들러 매핑까지 가지 못하고 401로 끊긴다. 엔드포인트가 존재하는지 여부와 무관하다.

- [ ] **Step 3: actuator 의존성을 추가한다**

`build.gradle`의 dependencies 블록에서 `spring-boot-starter-webmvc` 바로 아래 줄에 추가한다.

```groovy
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

**버전을 적지 않는다.** `io.spring.dependency-management` 플러그인이 Boot 4.0.7 BOM에서 가져온다.

- [ ] **Step 4: 여전히 실패하는지 확인한다**

```bash
./gradlew test --tests '*SecurityConfigTest'
```

Expected: 여전히 FAIL, `Status expected:<200> but was:<401>`. 엔드포인트는 생겼지만 `anyRequest().authenticated()`에 걸린 상태다. **상태 코드는 Step 2와 같다** — 시큐리티가 먼저 끊기 때문에 의존성 유무가 응답에 드러나지 않는다. 여기서 확인할 것은 "여전히 실패한다"뿐이다.

테스트가 두 절반을 모두 지킨다는 성질은 그대로다. 나중에 한쪽만 깨지면 각각 다른 코드로 잡힌다 — 의존성만 빠지면 경로는 공개 상태이므로 404, `PUBLIC_PATHS`에서만 빠지면 401이다.

- [ ] **Step 5: `PUBLIC_PATHS`에 헬스체크를 추가한다**

`SecurityConfig.java`의 `PUBLIC_PATHS` 배열을 아래로 바꾼다. `/actuator/**` 와일드카드를 쓰지 않는다.

```java
    private static final String[] PUBLIC_PATHS = {
            "/auth/signup",
            "/auth/login",
            "/auth/refresh",
            // 로드밸런서·모니터링이 토큰 없이 호출한다. /actuator/** 와일드카드를 쓰지 않는
            // 이유는 위 /auth/** 와 같다 — 이후 노출되는 actuator 엔드포인트가 딸려 열린다.
            // actuator 기본 노출은 health 하나뿐이고 show-details 기본값이 never라
            // 응답은 {"status":"UP"}뿐이다. 별도 설정을 두지 않는다.
            "/actuator/health",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
    };
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```bash
./gradlew test --tests '*SecurityConfigTest'
```

Expected: PASS (3개 테스트 전부).

- [ ] **Step 7: 전체 테스트가 깨지지 않았는지 확인한다**

actuator가 추가되면 모든 `@SpringBootTest`가 actuator 자동설정을 함께 올린다. 회귀가 없는지 본다.

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. 실패하면 `docker compose ps`로 postgres/redis가 healthy인지부터 확인한다.

- [ ] **Step 8: 커밋한다**

```bash
git add build.gradle src/main/java/jungkathon3team/aftergrow/common/config/SecurityConfig.java src/test/java/jungkathon3team/aftergrow/common/config/SecurityConfigTest.java
git commit -m "feat: actuator 헬스체크 추가하고 인증 없이 열리도록 공개 경로에 등록"
```

---

## Task 2: `Dockerfile`과 `.dockerignore`

설계 문서 §4.1에 해당한다.

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

**Interfaces:**
- Consumes: Task 1의 결과물이 이미지 안에 포함된다 (별도 코드 의존은 없다)
- Produces: `/app/app.jar`를 실행하고 8080을 여는 이미지. Task 4의 `docker-compose.prod.yml`과 Task 5의 `deploy.yml`이 이 Dockerfile을 빌드한다.

- [ ] **Step 1: `.dockerignore`를 먼저 만든다**

빌드 컨텍스트에 로컬 `build/` 산출물이 딸려 들어가면 이미지 안에 낡은 jar가 섞일 수 있다. Dockerfile보다 먼저 만든다.

`.dockerignore`:

```
.git
.github
.gradle
build
.idea
docs
*.iml
.env
.env.local
src/main/resources/application-local.yml
```

`gradle/wrapper/gradle-wrapper.jar`는 **제외하지 않는다** — 이미지 안에서 `./gradlew`가 이 파일을 필요로 한다.

- [ ] **Step 2: `Dockerfile`을 만든다**

```dockerfile
# 빌드 스테이지는 GitHub Actions 러너에서만 실행된다.
# EC2(t2.micro, 1GB)에서 이 이미지를 빌드하지 말 것 — Gradle이 메모리를 다 써서
# 같은 호스트의 postgres/app이 OOM killer에 종료된다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src

# 의존성 레이어를 소스와 분리해 소스만 바뀔 때 캐시가 살아남게 한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
# bootJar만 실행한다. jar 태스크를 함께 돌리면 -plain.jar가 생겨
# 아래 COPY의 와일드카드가 두 개를 잡는다.
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
# 1GB 호스트에 postgres/redis가 함께 올라가므로 힙을 컨테이너 메모리의 절반으로 제한한다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 3: 이미지가 빌드되는지 확인한다**

```bash
docker build -t aftergrow:local .
```

Expected: `naming to docker.io/library/aftergrow:local` 로 끝나며 성공. 첫 빌드는 Gradle 의존성 다운로드로 몇 분 걸린다.

- [ ] **Step 4: 이미지 안에 jar가 하나만 있는지 확인한다**

`-plain.jar`가 함께 잡히면 `app.jar`가 실행 불가능한 쪽으로 덮어써진다. 실제로 확인한다.

```bash
docker run --rm --entrypoint ls aftergrow:local -l /app
```

Expected: `app.jar` **한 개**만 보인다.

- [ ] **Step 5: 컨테이너가 실행되고 DB를 찾는 단계까지 가는지 확인한다**

DB 없이 띄우면 기동에 실패하는 게 정상이다. 확인하려는 건 "jar가 실행 가능하고 Spring이 뜨기 시작한다"까지다.

```bash
docker run --rm aftergrow:local
```

Expected: Spring Boot 배너가 출력되고 datasource 연결 실패로 종료된다. **배너가 보이면 성공**이다. `no main manifest attribute` 나 `Unable to access jarfile`이 나오면 Step 2로 돌아간다.

- [ ] **Step 6: 커밋한다**

```bash
git add Dockerfile .dockerignore
git commit -m "chore: 앱 이미지 빌드용 Dockerfile 추가"
```

---

## Task 3: `application-prod.yml`과 `.env.example`

설계 문서 §4.3, §5에 해당한다.

**Files:**
- Create: `src/main/resources/application-prod.yml`
- Modify: `.env.example`

**Interfaces:**
- Consumes: 없음
- Produces: `prod` 프로파일. 아래 환경변수를 요구한다 — Task 4의 compose가 전부 주입해야 한다.
  - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
  - `SPRING_DATA_REDIS_HOST`
  - `JWT_SECRET`

- [ ] **Step 1: `application-prod.yml`을 만든다**

`src/test/resources/application-test.yml`과 같은 역할이다. 비밀이 아닌 값만 커밋한다.

```yaml
# 배포(prod 프로파일) 전용 설정입니다.
#
# datasource / redis 접속 정보는 docker-compose.prod.yml이 환경변수
# (SPRING_DATASOURCE_URL 등)로 주입하므로 여기에 두지 않습니다.
# jwt.secret도 값을 적지 않고 환경변수를 참조합니다 — 이 파일은 커밋됩니다.
spring:
  jpa:
    hibernate:
      # 엔티티와 Flyway 스키마가 어긋나면 기동 시점에 잡히도록 local/test와 동일하게 validate
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: ${JWT_SECRET}
  access-token-expiration-ms: 3600000
  refresh-token-expiration-ms: 2592000000
```

- [ ] **Step 2: `.env.example`에 새 키가 필요한지 확인한다**

prod가 요구하는 키는 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET` 네 개다. 실제로 있는지 확인한다.

```bash
grep -E '^(POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD|JWT_SECRET)=' .env.example
```

Expected: 네 줄 모두 출력된다. **전부 이미 있으므로 `.env.example`은 수정하지 않는다.**

`.env.example`의 `REDIS_HOST=localhost`는 로컬 개발용이다. prod에서는 compose가 `SPRING_DATA_REDIS_HOST: redis`(컨테이너 이름)를 직접 주입하므로 `.env`에서 읽지 않는다. 헷갈릴 수 있으니 해당 줄에 주석 한 줄만 덧붙인다.

```
# Redis
REDIS_HOST=localhost   # 로컬 개발용. 배포는 docker-compose.prod.yml이 redis 컨테이너명을 직접 주입합니다
REDIS_PORT=6379
```

네 키 중 하나라도 빠져 있으면 그때만 이름을 추가한다 (값은 `change-me`).

- [ ] **Step 3: prod 프로파일로 실제 기동되는지 확인한다**

**이 단계가 이 태스크의 핵심 검증이다.** `${JWT_SECRET}` 같은 참조가 하나라도 해소되지 않으면 `PlaceholderResolutionException`으로 죽는데, 그 사고는 원래 배포된 서버에서만 발견된다. 여기서 미리 잡는다.

로컬 Docker의 postgres/redis를 그대로 쓴다. `POSTGRES_*` 값은 본인 `.env`의 값으로 바꾼다.

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/aftergrow \
SPRING_DATASOURCE_USERNAME=dev \
SPRING_DATASOURCE_PASSWORD=<본인 .env의 POSTGRES_PASSWORD> \
SPRING_DATA_REDIS_HOST=localhost \
JWT_SECRET=local-verification-secret-at-least-32-characters \
./gradlew bootRun
```

Expected: `Started AftergrowApplication` 이 뜬다. 다른 터미널에서 확인한다.

```bash
curl localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`

확인 후 `Ctrl+C`로 종료한다. `PlaceholderResolutionException`이 나오면 메시지에 적힌 키를 `application-prod.yml`이나 위 환경변수 목록에 채운다.

- [ ] **Step 4: 커밋한다**

```bash
git add src/main/resources/application-prod.yml .env.example
git commit -m "chore: prod 프로파일 설정 추가"
```

---

## Task 4: `docker-compose.prod.yml`

설계 문서 §4.2에 해당한다.

**Files:**
- Create: `docker-compose.prod.yml`

**Interfaces:**
- Consumes: Task 2의 이미지(`ghcr.io/mju-jungkathon/aftergrow:latest`), Task 3의 `prod` 프로파일과 환경변수 목록
- Produces: EC2에서 `docker compose -f docker-compose.prod.yml up -d`로 뜨는 스택

- [ ] **Step 1: `docker-compose.prod.yml`을 만든다**

```yaml
# EC2 배포용입니다. 로컬 개발은 docker-compose.yml을 쓰세요.
#
# app은 빌드하지 않고 GHCR에서 받습니다 — EC2(t2.micro, 1GB)에서 Gradle 빌드를
# 돌리면 같은 호스트의 postgres/app이 OOM killer에 종료됩니다.
#   docker compose -f docker-compose.prod.yml pull
#   docker compose -f docker-compose.prod.yml up -d
services:
  app:
    image: ghcr.io/mju-jungkathon/aftergrow:latest
    container_name: aftergrow-app
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      # application.yml의 active: local 하드코딩을 환경변수가 덮어씁니다
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    # ponytail: app에는 healthcheck를 두지 않습니다. JRE 이미지에 curl/wget이 없어
    # 한 줄로 끝나지 않고, 단일 호스트에서 헬스체크가 할 수 있는 일은 재시작뿐이라
    # restart: unless-stopped와 겹칩니다. ALB를 붙일 때 /actuator/health로 추가하세요.

  postgres:
    image: postgres:16
    container_name: aftergrow-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    # ports를 공개하지 않습니다. app이 컨테이너 네트워크로만 접근합니다.
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7
    container_name: aftergrow-redis
    restart: unless-stopped
    # ports를 공개하지 않습니다.
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

- [ ] **Step 2: compose 파일이 유효하고 변수가 해소되는지 확인한다**

```bash
docker compose -f docker-compose.prod.yml config
```

Expected: 병합된 설정이 출력된다. `${POSTGRES_DB}` 같은 변수가 로컬 `.env` 값으로 치환되어 보인다. `JWT_SECRET`이 비어 있다는 경고가 나오면 로컬 `.env`에 `JWT_SECRET`을 추가한다 (Task 3 Step 2에서 `.env.example`에 이름을 넣었다).

- [ ] **Step 3: postgres/redis 포트가 공개되지 않는지 확인한다**

설계 문서 §6의 보안 판단이다. 눈으로 넘기지 말고 실제로 확인한다.

```bash
docker compose -f docker-compose.prod.yml config | grep -A3 "published"
```

Expected: `published: "8080"` **하나만** 나온다. `5432`나 `6379`가 보이면 해당 서비스의 `ports:` 블록을 지운다.

- [ ] **Step 4: 커밋한다**

```bash
git add docker-compose.prod.yml
git commit -m "chore: EC2 배포용 docker-compose.prod.yml 추가"
```

---

## Task 5: `.github/workflows/deploy.yml`

설계 문서 §4.4에 해당한다.

**Files:**
- Create: `.github/workflows/deploy.yml`

**Interfaces:**
- Consumes: Task 2의 `Dockerfile`, Task 4의 `docker-compose.prod.yml`(EC2에서 실행됨)
- Produces: `ghcr.io/mju-jungkathon/aftergrow:latest` 와 `:{sha}` 태그, 그리고 EC2에 배포된 스택. Task 7이 이 워크플로가 성공하는지 확인한다.
- 필요한 리포지토리 시크릿: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY` (Step 2에서 등록)

- [ ] **Step 1: EC2의 레포 경로를 확인한다**

워크플로에 박아 넣어야 하므로 먼저 알아야 한다. EC2에 SSH로 접속해:

```bash
find ~ -maxdepth 3 -name docker-compose.yml -printf '%h\n'
```

Expected: `/home/ubuntu/jungkathon3teamBE`. Step 3의 워크플로에 이 값이 이미 적혀 있으므로 **다르게 나오면 워크플로의 `EC2_APP_DIR`을 실제 값으로 고친다.**

- [ ] **Step 2: 배포용 SSH 키를 만들고 시크릿을 등록한다**

**EC2 접속에 쓰던 기존 키를 재사용하지 않는다.** Actions에 넣은 키가 노출되면 인스턴스 전체 접근이 넘어가므로, 유출 시 그 키만 `authorized_keys`에서 지울 수 있어야 한다.

로컬에서 키를 만든다 (passphrase 없이 — 워크플로가 입력할 수 없다):

```bash
ssh-keygen -t ed25519 -f ~/.ssh/aftergrow_deploy -N "" -C "github-actions-deploy"
```

공개키를 EC2에 등록한다. `<EC2_IP>`와 기존 키 경로는 본인 값으로:

```bash
ssh -i ~/.ssh/<기존키>.pem ubuntu@<EC2_IP> "echo '$(cat ~/.ssh/aftergrow_deploy.pub)' >> ~/.ssh/authorized_keys"
```

새 키로 접속되는지 먼저 확인한다:

```bash
ssh -i ~/.ssh/aftergrow_deploy ubuntu@<EC2_IP> "echo OK"
```

Expected: `OK`. 실패하면 시크릿을 등록하기 전에 여기서 해결한다.

시크릿 세 개를 등록한다:

```bash
gh secret set EC2_HOST --body "<EC2 퍼블릭 IP 또는 도메인>"
gh secret set EC2_USER --body "ubuntu"
gh secret set EC2_SSH_KEY < ~/.ssh/aftergrow_deploy
```

`EC2_SSH_KEY`는 **개인키 전문**(`-----BEGIN OPENSSH PRIVATE KEY-----` 포함)이라 `--body` 대신 파일 리다이렉트를 쓴다.

등록을 확인한다:

```bash
gh secret list
```

Expected: `EC2_HOST`, `EC2_SSH_KEY`, `EC2_USER` 세 개가 보인다.

- [ ] **Step 3: 워크플로를 만든다**

`EC2_APP_DIR`은 Step 1에서 확인한 값으로 바꾼다.

```yaml
name: Deploy

# test.yml은 main 대상 PR에서 돌고, 이 워크플로는 main push(= 머지)에서 돕니다.
# 트리거가 겹치지 않으므로 test.yml은 수정하지 않습니다.
on:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    env:
      # EC2에 클론된 레포 경로. 비밀이 아니므로 시크릿이 아니라 여기에 둡니다.
      EC2_APP_DIR: /home/ubuntu/jungkathon3teamBE

    steps:
      - uses: actions/checkout@v4

      - name: GHCR 로그인
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: 이미지 빌드 & 푸시
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          # latest는 EC2가 pull 할 대상, sha는 어느 커밋이 배포됐는지 확인하고
          # 되돌리기 위한 것입니다.
          tags: |
            ghcr.io/mju-jungkathon/aftergrow:latest
            ghcr.io/mju-jungkathon/aftergrow:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: EC2에 배포
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          # 중간 명령이 실패하면 거기서 멈춥니다. 없으면 pull이 실패해도
          # 마지막 명령 결과만 보고 초록불이 됩니다.
          script_stops: true
          script: |
            cd ${{ env.EC2_APP_DIR }}
            # 이미지는 GHCR에서 오지만 compose 파일은 EC2의 것을 씁니다.
            # 순서가 뒤바뀌면 낡은 compose로 새 이미지를 띄우게 됩니다.
            git pull origin main
            docker compose -f docker-compose.prod.yml pull
            docker compose -f docker-compose.prod.yml up -d
            # up -d는 컨테이너를 띄우기만 하고 앱 기동을 기다리지 않습니다.
            # 이 줄이 없으면 기동에 실패한 배포도 초록불이 됩니다.
            timeout 90 sh -c 'until curl -fs localhost:8080/actuator/health; do sleep 3; done'
            # :latest가 옮겨가면서 태그 없는 이전 이미지가 쌓입니다.
            # t2.micro 기본 EBS는 8GB라 몇 주면 찹니다. 헬스체크 뒤에 지워야
            # 실패했을 때 이전 이미지로 되돌릴 여지가 남습니다.
            docker image prune -f
```

- [ ] **Step 4: 워크플로 문법을 검사한다**

**깨진 워크플로는 `main`에 머지된 뒤에야 발견된다.** actionlint로 미리 잡는다 — YAML 문법뿐 아니라 잘못된 `uses:` 참조와 시크릿/컨텍스트 오타까지 본다. Docker로 돌리므로 설치할 것은 없다 (Task 2~4에서 이미 Docker를 쓰고 있다).

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd):/repo" --workdir /repo rhysd/actionlint:latest -color
```

Expected: 출력 없음(= 문제 없음), 종료 코드 0. `deploy.yml`과 기존 `test.yml` 둘 다 검사된다.

`MSYS_NO_PATHCONV=1`은 Windows Git Bash용이다. 없으면 Git Bash가 `/repo`를 윈도우 경로로 바꿔버려 컨테이너가 파일을 못 찾는다. macOS·Linux에서는 붙이지 않아도 된다.

`${{ secrets.EC2_* }}`에 대한 경고가 나오면 Task 5 Step 2에서 시크릿을 등록했는지 확인한다 — actionlint는 시크릿 존재 여부를 알 수 없으므로 이 경고는 무시해도 된다.

Windows PowerShell에서는 `$(pwd)` 대신 `${PWD}`를 쓴다.

- [ ] **Step 3: 커밋하고 푸시한다**

```bash
git add .github/workflows/deploy.yml
git commit -m "chore: GHCR 이미지 빌드·푸시 워크플로 추가"
git push -u origin feature/deploy-pipeline
```

- [ ] **Step 4: PR을 만들고 CI가 통과하는지 확인한다**

```bash
gh pr create --base main --title "chore: 배포 산출물 레포화 및 actuator 헬스체크 추가" --body "$(cat <<'EOF'
설계 문서: `docs/superpowers/specs/2026-08-12-deployment-readiness-design.md`

- actuator 추가 + `/actuator/health` 공개 (문제 1, 2)
- Dockerfile / docker-compose.prod.yml / application-prod.yml / deploy.yml 을 레포로 (문제 3)
- 이미지 빌드는 Actions에서만 — EC2(1GB)에서 Gradle 빌드 시 OOM 위험

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: `test.yml`이 돌아 통과한다. `deploy.yml`은 PR에서는 돌지 않는다 (`main` push 트리거).

---

## Task 6: `CLAUDE.md` 갱신

**Files:**
- Modify: `CLAUDE.md:43` (설정 키 경고 문단), 설정 구조 절 끝

**Interfaces:**
- Consumes: Task 1~5의 결과물
- Produces: 없음 (문서)

> **주의:** 작업 트리에 이 계획과 무관한 `CLAUDE.md` 수정이 이미 있을 수 있다. `git diff CLAUDE.md`로 확인하고, 남의 변경을 되돌리지 말고 아래 내용만 덧붙인다.

- [ ] **Step 1: 설정 키 경고를 세 파일 기준으로 고친다**

`CLAUDE.md:43`의 아래 줄을

```
> ⚠️ **설정 키를 추가할 때는 `application-local.yml`과 `application-test.yml` 양쪽에 넣으세요.** local에만 넣으면 로컬 테스트는 통과하고 CI만 `PlaceholderResolutionException`으로 죽습니다. 실제로 `jwt.*`에서 한 번 겪었습니다.
```

이렇게 바꾼다.

```
> ⚠️ **설정 키를 추가할 때는 세 파일 전부에 넣으세요** — `application-local.yml`(로컬), `src/test/resources/application-test.yml`(CI), `src/main/resources/application-prod.yml`(배포). local에만 넣으면 로컬 테스트는 통과하고 CI만 `PlaceholderResolutionException`으로 죽습니다. 실제로 `jwt.*`에서 한 번 겪었습니다. **prod만 빠뜨리면 로컬과 CI가 전부 통과하고 배포된 서버만 기동 시 죽습니다** — 가장 늦게 발견되는 형태입니다.
```

- [ ] **Step 2: 배포 절에 내용을 추가한다**

"## 설정 구조" 절의 마지막(`gradlew`의 `100755` 문단 뒤)에 아래를 덧붙인다.

```markdown
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
```

- [ ] **Step 3: 커밋하고 푸시한다**

```bash
git add CLAUDE.md
git commit -m "docs: 배포 절차와 설정 파일 3곳 규칙을 CLAUDE.md에 반영"
git push
```

---

## Task 7: EC2 전환 (최초 1회)

설계 문서 §9에 해당한다. **PR이 `main`에 머지된 뒤에** 수행한다.

> **머지 직후 `Deploy` 워크플로의 첫 실행은 SSH 단계에서 실패하는 것이 정상이다.** 이미지 빌드·푸시는 성공하지만, EC2가 아직 GHCR 로그인도 새 compose도 없는 상태라 배포 단계가 멈춘다. **EC2에는 아무 변화도 일어나지 않으므로 안전하다** — 이 태스크가 EC2를 준비시키고, Step 11에서 워크플로를 다시 돌려 자동 경로가 끝까지 도는 것을 확인한다.
>
> 첫 실행을 빨간불로 남기고 싶지 않다면 Task 5 Step 2(시크릿 등록)와 이 태스크의 Step 3~9를 머지 전에 미리 해둬도 된다. 그 경우 EC2에서 `git checkout feature/deploy-pipeline`으로 compose 파일을 먼저 받아야 한다.

**Files:** 없음 (EC2에서 실행하는 절차)

**Interfaces:**
- Consumes: Task 4의 `docker-compose.prod.yml`, Task 5가 올린 GHCR 이미지와 등록된 시크릿
- Produces: 새 스택으로 돌아가는 EC2. 이후 배포는 `main` 머지만으로 자동 수행된다

- [ ] **Step 1: 이미지가 GHCR에 올라갔는지 확인한다**

```bash
gh run list --workflow=Deploy --limit 3
gh run view --log-failed | tail -20
```

Expected: 최신 실행이 실패했더라도 **`이미지 빌드 & 푸시` 단계는 성공**이고, 실패한 단계가 `EC2에 배포`다. 리포지토리 Packages에 `aftergrow`가 보이는지도 확인한다.

빌드 단계에서 실패했다면 EC2를 건드리기 전에 그것부터 고친다.

- [ ] **Step 2: EC2에서 기존 설정을 백업한다**

되돌릴 수 있어야 한다. EC2에 SSH로 접속해 실행한다.

```bash
cd /home/ubuntu/jungkathon3teamBE
cp docker-compose.yml ~/backup-compose.yml
cp Dockerfile ~/backup-Dockerfile 2>/dev/null || true
```

- [ ] **Step 3: GHCR에 로그인한다 (최초 1회)**

GitHub에서 `read:packages` 권한의 PAT를 발급한 뒤:

```bash
echo <PAT> | docker login ghcr.io -u <github-username> --password-stdin
```

Expected: `Login Succeeded`

- [ ] **Step 4: 손으로 고친 파일을 정리하고 레포를 받는다**

**이 단계에서 막힐 가능성이 가장 높다.** 문제 3의 원인이 EC2에서 `docker-compose.yml`을 직접 고친 것이므로, 그 로컬 수정이 `git pull`을 막는다. 먼저 확인한다.

```bash
git status --short
```

`M docker-compose.yml` 같은 줄이 보이면, Step 2에서 백업했으므로 그 수정을 버린다. 앞으로 이 파일은 로컬 개발용이고 EC2가 쓰는 것은 `docker-compose.prod.yml`이다.

```bash
git checkout -- docker-compose.yml
git status --short          # 다른 수정 파일이 더 있으면 각각 판단한다
```

브랜치가 `main`인지 확인하고 받는다. 자동 배포 스크립트도 `git pull origin main`을 쓰므로 여기서 맞춰둬야 한다.

```bash
git branch --show-current   # main 이 아니면 git checkout main
git pull origin main
ls docker-compose.prod.yml  # 파일이 실제로 왔는지 확인
```

Expected: `docker-compose.prod.yml`이 보인다.

이어서 `.env`를 확인한다.

```bash
grep -c JWT_SECRET .env
```

Expected: `1`. `0`이면 `.env`에 `JWT_SECRET=<32자 이상의 임의 문자열>`을 추가한다. `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD`도 있는지 함께 확인한다.

- [ ] **Step 5: 전환 전 DB 상태를 확인하고 백업한다**

**이 브랜치의 전제는 EC2의 `docker-compose.yml`이 손으로 고쳐진 뒤 한 번도 커밋되지 않았다는 것이다** — 즉 지금 postgres가 실제로 어떤 볼륨(또는 바인드 마운트)을 쓰고 있는지 아무도 확인한 적이 없다. Step 4에서 `git checkout --`로 그 파일을 버리기 전에, 새 compose가 선언하는 `pgdata` 볼륨과 지금 컨테이너가 실제로 쓰는 마운트가 같은 것인지 확인해야 한다. 다르면(바인드 마운트였거나 볼륨 이름이 다르면) 새 스택은 빈 볼륨으로 떠서 Flyway가 깨끗하게 마이그레이션하고 헬스체크도 통과하지만, 기존 사용자·러닝 세션이 앱 입장에서는 전부 사라진 것과 같다.

```bash
docker inspect aftergrow-postgres --format '{{json .Mounts}}'
docker volume ls | grep pgdata
docker exec aftergrow-postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > ~/pre-cutover.sql
ls -la ~/pre-cutover.sql
```

`docker inspect`가 보여주는 마운트 소스와 `docker volume ls`에 있는 `pgdata` 볼륨(이름에 프로젝트 접두사가 붙을 수 있다)을 비교한다. 일치하지 않으면 Step 6 이후로 진행하지 않는다 — 새 compose의 `pgdata` 볼륨 이름을 실제 볼륨에 맞추거나, 기존 볼륨을 `pgdata`로 옮기고 나서 재개한다.

일치 여부와 무관하게 `pre-cutover.sql`은 **이 전환의 유일한 안전망**이다. 파일 크기가 0이 아닌 것을 `ls -la`로 확인한 뒤에만 다음 단계로 진행한다.

이어서 Flyway 이력 테이블이 있는지 확인한다 — `application-prod.yml`은 `ddl-auto: validate`로 Flyway를 켜지만 `baseline-on-migrate`가 없다. 기존 DB가 `ddl-auto: update`로 만들어졌다면(즉 Flyway 없이 Hibernate가 스키마를 만들었다면) `flyway_schema_history` 테이블이 없고, 새 app 컨테이너는 `Found non-empty schema "public" without schema history table` 에러로 기동에 실패한다 — 이미 `up -d`가 이전 컨테이너를 내린 뒤이므로 그 자리에서 서비스가 끊긴다.

```bash
docker exec aftergrow-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\dt flyway_schema_history'
```

테이블이 없으면(`Did not find any relation`), **전환을 진행하기 전에** `application-prod.yml`에 `baseline-on-migrate: true`와 `baseline-version: 0`을 추가해야 한다. 이건 EC2에서 손으로 고칠 값이 아니라 코드 변경이다 — 별도 커밋으로 만들어 이미지를 다시 빌드·배포한 뒤에 이 태스크를 재개한다.

- [ ] **Step 6: 기존 스택을 내린다**

**`-v`를 붙이지 않는다.** 붙이면 DB 볼륨이 삭제되어 데이터를 잃는다.

```bash
docker compose down
```

- [ ] **Step 7: 새 스택을 띄운다**

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

Expected: `aftergrow-app`, `aftergrow-postgres`, `aftergrow-redis` 세 개가 `Up`. app은 postgres/redis가 healthy가 될 때까지 기다렸다 뜬다.

- [ ] **Step 8: 헬스체크를 확인한다**

```bash
curl localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`

`{"status":"DOWN"}`이면 `docker compose -f docker-compose.prod.yml logs app`으로 원인을 본다. 대개 `.env`의 DB 접속 정보 불일치다.

- [ ] **Step 9: DB 포트가 외부에 열려 있지 않은지 확인한다**

prod compose는 5432를 공개하지 않지만, 기존 컨테이너나 보안 그룹 설정이 남아 있을 수 있다.

```bash
docker ps --format '{{.Names}}\t{{.Ports}}'
```

Expected: `aftergrow-app`만 `0.0.0.0:8080->8080/tcp`를 갖고, postgres/redis에는 `0.0.0.0:` 매핑이 없다.

이어서 **AWS 콘솔의 보안 그룹에서 5432·6379 인바운드 규칙이 있으면 삭제한다.**

- [ ] **Step 10: 실제 API가 동작하는지 확인한다**

헬스체크만으로는 앱이 일하는지 알 수 없다. 로그인 후 인증이 필요한 엔드포인트를 호출한다.

```bash
curl -s -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"<기존 계정>","password":"<비밀번호>"}'
```

Expected: `"success": true` 와 `accessToken`. 계정이 없으면 `/auth/signup`으로 하나 만든다.

받은 토큰으로:

```bash
curl -s localhost:8080/home -H "Authorization: Bearer <accessToken>"
```

Expected: `"success": true`.

- [ ] **Step 11: 자동 배포가 끝까지 도는지 확인한다**

여기까지는 손으로 한 것이라 자동 경로가 동작한다는 증거가 아직 없다. Step 1에서 실패했던 워크플로를 다시 돌려 확인한다. 로컬에서:

```bash
gh run list --workflow=Deploy --limit 1
gh run rerun <run-id> --failed
gh run watch
```

Expected: `EC2에 배포` 단계까지 초록. 이 단계가 성공했다는 것은 SSH 접속 → `git pull` → `compose pull` → `up -d` → 헬스체크 200이 전부 통과했다는 뜻이다.

**여기서 실패하면 그 사유가 자동 배포의 실제 결함이다.** 자주 나오는 것:

| 로그에 보이는 것 | 원인 |
|---|---|
| `Permission denied (publickey)` | `EC2_SSH_KEY` 시크릿이 개인키 전문이 아니거나 공개키가 `authorized_keys`에 없다 |
| `denied: ... unauthorized` | EC2의 `docker login ghcr.io`가 안 됐거나 PAT가 만료됐다 (Step 3) |
| `cd: no such file or directory` | 워크플로의 `EC2_APP_DIR`이 실제 경로와 다르다 (Task 5 Step 1) |
| `timeout ... until curl` 에서 멈춤 | 앱이 180초 안에 뜨지 못했다. EC2에서 `docker compose -f docker-compose.prod.yml logs app` |

- [ ] **Step 12: 배포가 실제로 반영되는지 한 번 확인한다**

워크플로가 초록이어도 "새 이미지가 실제로 반영되는가"는 아직 확인되지 않았다. 사소한 변경 하나로 확인한다.

`README.md`에 한 줄을 더하고 `main`에 머지한 뒤, 배포된 이미지의 커밋이 바뀌는지 본다.

```bash
gh run watch                                    # Deploy가 초록으로 끝날 때까지
ssh -i ~/.ssh/aftergrow_deploy ubuntu@<EC2_IP> \
  "docker inspect aftergrow-app --format '{{.Config.Image}}' && cd /home/ubuntu/jungkathon3teamBE && git log --oneline -1"
```

Expected: EC2의 최신 커밋이 방금 머지한 커밋이고, 컨테이너가 새로 뜬 상태다.

---

## 완료 기준

설계 문서 §11과 같다.

- [ ] `./gradlew test` 통과 (신규 actuator 테스트 포함)
- [ ] `docker build .` 성공
- [ ] `main` 머지 후 GHCR에 `:latest`와 `:{sha}` 태그가 올라감
- [ ] `Deploy` 워크플로가 `EC2에 배포` 단계까지 초록으로 끝남
- [ ] EC2에서 `curl localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] EC2 외부에서 5432 접속이 거부됨
- [ ] 로그인 → `/home` 호출이 배포된 서버에서 정상 동작
- [ ] `main`에 머지하면 사람 개입 없이 EC2에 반영됨 (Task 7 Step 12)

## 이번 범위 밖

설계 문서 §10 참고. 무중단 배포, 롤백 자동화, HTTPS/ALB, DB 백업, 로그 수집은 별도 작업이다.

`up -d`는 기존 컨테이너를 내리고 새로 띄우므로 **배포 때마다 수십 초 끊긴다.** 단일 인스턴스에서는 피할 수 없다. 시연 중에 `main`에 머지하지 않도록 주의한다.
