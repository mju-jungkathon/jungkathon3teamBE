# 배포 산출물 레포화 · actuator 헬스체크 설계

- 작성일: 2026-08-12
- 브랜치: `feature/deploy-pipeline` (PR 대상 `main`)
- 대상: 문제 1(actuator 부재), 문제 2(헬스체크 401), 문제 3(EC2 배포 설정 미커밋)
- 범위 밖: R5 AI 회복 가이드, 외부 API 실연동, ALB/도메인/HTTPS

## 1. 배경

EC2에 배포는 되어 있지만 **배포에 필요한 산출물이 레포에 없다.** 확인된 상태는 다음과 같다.

| 배포에 필요한 것 | 레포 상태 |
|---|---|
| `Dockerfile` | 없음 (EC2에만 존재) |
| app 서비스가 포함된 compose | 없음. `docker-compose.yml`은 postgres·redis만 |
| prod 프로파일 설정 | 없음 |
| 배포 워크플로 | 없음 (`.github/workflows/`에 `test.yml`뿐) |
| actuator | `build.gradle`에 의존성 없음 |

지금 EC2 인스턴스를 잃으면 레포만으로 재현할 수 있는 것은 postgres·redis 컨테이너뿐이다. 문제 3은 `docker-compose.yml` 한 파일의 문제가 아니라 배포 산출물 전체가 서버에만 있는 상태다.

문제 1과 2는 별개 이슈가 아니라 **한 변경의 두 절반**이다. 의존성만 넣으면 `SecurityConfig`의 `anyRequest().authenticated()`에 걸려 401, `PUBLIC_PATHS`만 넣으면 엔드포인트가 없어 404다. 둘 다 있어야 200이 나오므로 하나의 커밋으로 묶는다.

## 2. 제약: EC2에서 빌드하지 않는다

EC2는 t2/t3.micro(메모리 1GB)이고 postgres·redis·app이 같은 호스트에 올라가 있다. 이 위에서 Gradle 빌드를 돌리면 Gradle 데몬과 Boot 4 컴파일만으로 메모리를 거의 다 쓰기 때문에, **빌드 중에 돌아가던 postgres나 app이 OOM killer에 종료될 수 있다.** 배포할 때마다 서비스가 죽는 형태의 사고다.

따라서 이미지 빌드는 GitHub Actions 러너에서만 수행하고, EC2는 완성된 이미지를 받기만 한다. 이 결정이 문제 3의 근본 원인(서버에서 파일을 직접 고치는 흐름)을 구조적으로 제거한다 — EC2에 빌드 컨텍스트가 없으면 서버에서 코드를 고칠 방법도 없다.

검토했다가 버린 대안:

- **jar만 CI에서 만들어 scp로 올리고 EC2는 COPY만** — 레지스트리가 필요 없어 더 단순하지만, jar를 올리는 수동 단계가 매 배포마다 남는다.
- **swap 2GB 추가 후 지금처럼 EC2에서 빌드** — 지금 구조를 가장 적게 건드리지만, 배포 중 서비스가 죽을 위험과 문제 3의 재발 가능성이 그대로 남는다.

## 3. 파이프라인

```
push to main
   └─ GitHub Actions
        ├─ test.yml        (기존, 변경 없음)
        └─ deploy.yml      (신규)
             ├─ 1. 이미지 빌드 → ghcr.io/mju-jungkathon/aftergrow:latest, :{sha}
             └─ 2. SSH로 EC2 접속 → git pull + compose pull + up -d + 헬스체크
                                       │
EC2 (t2.micro)                         │
   └─ docker-compose.prod.yml ─────────┘
        ├─ app       ← GHCR 이미지 (빌드하지 않음), 8080만 공개
        ├─ postgres  ← 포트 미공개
        └─ redis     ← 포트 미공개
```

`main`에 머지되면 배포까지 자동으로 끝난다. EC2에 사람이 들어가는 것은 최초 1회 설정(3.1) 때뿐이다.

### 3.1 EC2에 최초 1회만 필요한 것

- `docker login ghcr.io` (PAT, `read:packages`) — GHCR 패키지가 private이므로 필요하다. 이 로그인은 `~/.docker/config.json`에 남아 이후 배포에서 재사용된다.
- 레포 클론 — EC2는 이미지를 빌드하지 않지만 `docker-compose.prod.yml`은 필요하다. `git pull`로 이 파일만 최신으로 유지한다.
- `.env` — 비밀값은 계속 EC2에만 둔다. 워크플로가 만들지 않는다.

### 3.2 필요한 리포지토리 시크릿

| 시크릿 | 값 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| `EC2_USER` | SSH 사용자 (Ubuntu AMI면 `ubuntu`) |
| `EC2_SSH_KEY` | EC2 접속용 **개인키 전문** (`-----BEGIN ... KEY-----` 포함) |

`.env`의 값(`JWT_SECRET`, DB 비밀번호)은 시크릿에 넣지 않는다. EC2에 이미 있고, 워크플로는 그 파일을 건드리지 않는다.

**배포용 SSH 키는 EC2 생성 시 쓴 키를 재사용하지 말고 따로 만드는 것을 권한다.** Actions에 넣은 키가 노출되면 인스턴스 전체 접근 권한이 넘어가므로, 유출 시 그 키만 `authorized_keys`에서 지울 수 있어야 한다.

## 4. 추가되는 파일

### 4.1 `Dockerfile`

멀티스테이지. 빌드 스테이지는 Actions 러너에서만 실행된다.

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
# 의존성 레이어를 소스와 분리해 캐시가 살아남게 한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
# bootJar 태스크만 실행하므로 build/libs에는 부트 jar 하나만 있다
# (jar 태스크가 만드는 -plain.jar는 생성되지 않는다)
COPY --from=build /src/build/libs/*.jar app.jar
# 1GB 호스트에서 컨테이너 메모리의 절반만 힙으로 쓴다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`gradlew`의 실행 권한은 이미지 안에서 `chmod +x`로 보장한다. 레포의 `gradlew`는 `100755`여야 한다는 기존 규칙(CLAUDE.md)은 그대로 유효하다.

### 4.2 `docker-compose.prod.yml`

기존 `docker-compose.yml`은 **로컬 개발용으로 그대로 둔다.** override 체인(`docker-compose.override.yml`)은 쓰지 않는다 — 그 파일은 `.gitignore` 대상이라 prod 설정을 커밋할 수 없다. 파일을 따로 두면 `.gitignore`는 손대지 않아도 된다.

```yaml
services:
  app:
    image: ghcr.io/mju-jungkathon/aftergrow:latest
    container_name: aftergrow-app
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
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

  postgres:
    image: postgres:16
    container_name: aftergrow-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    # ports를 공개하지 않는다 — app이 컨테이너 네트워크로만 접근한다
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
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

EC2에서 쓰던 `restart: always` 대신 `restart: unless-stopped`를 쓴다. 차이는 하나뿐이다 — 사람이 의도적으로 `docker stop` 한 컨테이너를 데몬 재시작 때 다시 살리지 않는다. 디버깅하려고 내려둔 컨테이너가 되살아나는 일을 막는다.

`app`에 컨테이너 healthcheck를 붙이지 않는다. JRE 이미지에 `curl`/`wget`이 없어 한 줄로 끝나지 않고, 단일 호스트에서 헬스체크가 할 수 있는 일은 재시작뿐이라 `restart: unless-stopped`와 효과가 겹친다. ALB를 붙일 때 필요해지므로 그 시점에 추가한다. compose 파일에 `ponytail:` 주석으로 남긴다.

### 4.3 `src/main/resources/application-prod.yml`

`application-test.yml`과 같은 역할이다. 비밀이 아닌 설정만 커밋하고, 접속 정보와 secret은 환경변수로 받는다.

```yaml
# 배포(prod 프로파일) 전용 설정입니다.
#
# datasource / redis 접속 정보는 docker-compose.prod.yml이 환경변수
# (SPRING_DATASOURCE_URL 등)로 주입하므로 여기에 두지 않습니다.
# jwt.secret도 값을 적지 않고 환경변수를 참조합니다.
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: ${JWT_SECRET}
  access-token-expiration-ms: 3600000
  refresh-token-expiration-ms: 2592000000
```

**`application.yml`의 `spring.profiles.active: local` 하드코딩은 수정하지 않는다.** 환경변수가 설정 파일보다 우선순위가 높아 `SPRING_PROFILES_ACTIVE=prod`가 덮어쓴다. 추측이 아니라 이 레포에서 이미 검증된 동작이다 — `test.yml`이 같은 하드코딩을 둔 채 `SPRING_PROFILES_ACTIVE=test`로 CI를 통과시키고 있다.

### 4.4 `.github/workflows/deploy.yml`

```yaml
name: Deploy

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
      # EC2에 클론된 레포 경로. 비밀이 아니므로 시크릿이 아니라 여기에 둔다.
      EC2_APP_DIR: /home/ubuntu/jungkathon3teamBE
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
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
          script_stops: true
          script: |
            cd ${{ env.EC2_APP_DIR }}
            git pull origin main
            docker compose -f docker-compose.prod.yml pull
            docker compose -f docker-compose.prod.yml up -d
            timeout 90 sh -c 'until curl -fs localhost:8080/actuator/health; do sleep 3; done'
            docker image prune -f
```

`:latest`와 `:{sha}` 두 개를 붙인다. `latest`는 EC2가 `pull` 할 대상이고, `{sha}`는 배포된 것이 어느 커밋인지 확인하고 되돌릴 수 있게 하기 위한 것이다.

SSH 스크립트에서 결정한 것들:

- **`script_stops: true`** — 중간 명령이 실패하면 거기서 멈추고 워크플로가 빨간불이 된다. 없으면 `pull`이 실패해도 마지막 명령의 결과만 보고 성공으로 끝난다.
- **`git pull`이 먼저다.** 이미지는 GHCR에서 오지만 `docker-compose.prod.yml`은 EC2의 파일을 쓴다. compose를 고친 배포에서 순서가 뒤바뀌면 낡은 compose로 새 이미지를 띄우게 된다.
- **`timeout 90 ... until curl`** — `up -d`는 컨테이너를 띄우기만 하고 앱이 뜰 때까지 기다리지 않는다. 이 줄이 없으면 기동에 실패한 배포도 초록불이 된다. 90초 안에 `/actuator/health`가 200을 주지 못하면 배포가 실패로 끝난다. 6절에서 actuator를 여는 이유가 여기서 처음으로 자동화에 쓰인다.
- **`docker image prune -f`** — `:latest` 태그가 매 배포마다 옮겨가면서 이전 이미지가 태그 없이 쌓인다. t2.micro의 기본 EBS는 8GB라 몇 주면 디스크가 찬다. 헬스체크가 끝난 뒤에 지워야 롤백 여지가 남는다.

`EC2_APP_DIR`은 비밀이 아니므로 시크릿이 아니라 워크플로 `env`에 둔다. 값은 EC2의 레포 경로다.

`test.yml`은 `main` 대상 PR에서 돌고 `deploy.yml`은 `main` push(= 머지)에서 돈다. 트리거가 겹치지 않으므로 `test.yml`은 수정하지 않는다.

## 5. 설정·시크릿

새 방식을 만들지 않고 이 레포가 CI에서 이미 쓰는 방식을 prod에 복제한다.

| 값 | 출처 |
|---|---|
| `spring.datasource.*`, `spring.data.redis.host` | 환경변수. Boot가 아는 이름이라 yml에 적지 않는다 |
| `ddl-auto`, flyway, jwt 만료시간 | `application-prod.yml` (커밋) |
| `JWT_SECRET`, `POSTGRES_PASSWORD` 등 | EC2의 `.env` (gitignore 대상) |

`.env.example`에 prod에서 쓰는 키 이름을 추가한다(값 없이 이름만). 기존 CLAUDE.md 규칙에 따른다.

**설정 키 추가 시 확인할 파일이 두 곳에서 세 곳으로 늘어난다.** 지금까지는 `application-local.yml`과 `application-test.yml`이었고 여기에 `application-prod.yml`이 더해진다. prod만 빠뜨리면 로컬과 CI는 전부 통과하고 **배포된 서버만 기동 시 `PlaceholderResolutionException`으로 죽는다** — 가장 늦게 발견되는 형태다. CLAUDE.md의 해당 경고 문단을 세 파일 기준으로 갱신한다.

GHCR 패키지는 **private**으로 둔다. EC2에서 최초 1회만 `read:packages` 권한의 PAT로 `docker login ghcr.io`를 수행한다. public으로 열면 로그인 단계가 없어져 더 간단하지만 컴파일된 백엔드 전체가 공개된다.

## 6. actuator (문제 1 + 2)

변경은 두 줄이다.

- `build.gradle` — `implementation 'org.springframework.boot:spring-boot-starter-actuator'` 추가
- `SecurityConfig.PUBLIC_PATHS` — `"/actuator/health"` 추가

**`/actuator/**` 와일드카드를 쓰지 않는다.** 와일드카드로 열면 이후 노출되는 actuator 엔드포인트가 자동으로 공개된다. `PUBLIC_PATHS`가 `/auth/**`를 거부하고 경로를 하나씩 나열하는 것과 같은 이유이며, 해당 판단은 `SecurityConfig`에 주석으로 이미 기록되어 있다.

**추가 설정은 두지 않는다.** actuator의 기본 HTTP 노출은 `health` 하나뿐이라 `/metrics`·`/env`는 애초에 열리지 않는다. `show-details` 기본값도 `never`라 응답은 `{"status":"UP"}`뿐이고, DB·Redis 접속 정보가 공개 엔드포인트로 새지 않는다. `/actuator` 디스커버리 엔드포인트는 `PUBLIC_PATHS`에 없으므로 인증이 필요한 상태로 남는다.

**Redis 장애 시 503을 반환하는 기본 동작을 유지한다.** actuator는 DB·Redis health indicator를 집계 상태에 포함한다. 이 앱은 refresh token을 Redis에만 저장하므로 Redis가 없으면 로그인과 재발급이 모두 실패한다. 그 상태에서 `UP`을 반환하는 것은 사실과 다르다.

## 7. 테스트

이 레포의 기존 방식(`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` 통합 테스트)을 따른다. 새 테스트는 하나다.

- **토큰 없이 `GET /actuator/health`가 200이고 `status`가 `UP`이다.**

이 테스트 하나가 문제 1과 2를 동시에 지킨다. 의존성이 없으면 404, `PUBLIC_PATHS`에 없으면 401, 둘 다 갖춰져야 200이므로 어느 쪽이 빠져도 실패한다.

`ApiResponse` 래퍼를 거치지 않는 응답이므로 검증은 `$.data.…`가 아니라 `$.status`다 — actuator가 직접 내려주는 형식이라 `GlobalExceptionHandler`나 공통 응답 규약과 무관하다. 이 도메인의 유일한 예외이므로 테스트에 주석으로 남긴다.

Dockerfile·compose·워크플로는 자동 테스트 대상이 아니다. 검증은 9절의 전환 절차에서 실제 배포로 확인한다.

## 8. 구현 순서

1. `build.gradle`에 actuator 추가 + `SecurityConfig.PUBLIC_PATHS`에 `/actuator/health` 추가
2. `/actuator/health` 통합 테스트 작성 → `./gradlew test` 통과 확인
3. `Dockerfile` 작성 → 로컬에서 `docker build .` 성공 확인
4. `application-prod.yml` + `.env.example` 키 추가
5. `docker-compose.prod.yml` 작성
6. `.github/workflows/deploy.yml` 작성
7. CLAUDE.md 갱신 (설정 파일 3곳 규칙, 배포 절차, actuator)
8. PR → `main` 머지 → `deploy.yml`이 GHCR에 이미지를 올리는지 확인
9. 9절 전환 절차 수행

1~2는 3 이후와 독립적이므로 먼저 머지해도 된다.

## 9. EC2 전환 절차

기존 컨테이너를 정리하고 새 구조로 갈아탄다. **DB 볼륨(`pgdata`)은 유지해야 하므로 `docker compose down -v`를 쓰지 않는다.**

1. EC2에서 현재 `docker-compose.yml`과 `Dockerfile`을 백업해 둔다 (되돌릴 때 필요).
2. `docker login ghcr.io` (PAT, `read:packages`) — 최초 1회.
3. 레포를 `git pull` 한다.
4. `.env`에 `JWT_SECRET`을 포함해 필요한 키가 모두 있는지 확인한다.
5. 기존 스택을 내린다: `docker compose down` (볼륨 유지).
6. `docker compose -f docker-compose.prod.yml pull`
7. `docker compose -f docker-compose.prod.yml up -d`
8. `curl localhost:8080/actuator/health` → `{"status":"UP"}` 확인.
9. 보안 그룹에서 5432·6379가 열려 있으면 닫는다. prod compose는 두 포트를 공개하지 않지만, 기존 설정이 남아 있을 수 있다.
10. 배포용 SSH 키를 만들어 공개키를 EC2의 `~/.ssh/authorized_keys`에 넣고, 개인키를 리포지토리 시크릿 `EC2_SSH_KEY`에 등록한다. `EC2_HOST`·`EC2_USER`도 함께 넣는다(3.2 참고).

**이후 배포는 `main`에 머지하는 것으로 끝난다.** 워크플로가 SSH로 들어가 `git pull` → `compose pull` → `up -d` → 헬스체크까지 수행한다. 사람이 EC2에 들어갈 일은 `.env`를 고칠 때뿐이다.

배포가 실패하면 워크플로 로그에 어느 명령에서 멈췄는지 남는다. 헬스체크 단계에서 실패했다면 EC2에서 `docker compose -f docker-compose.prod.yml logs app`을 본다.

## 10. 남는 것 (이번 범위 밖)

- **무중단 배포** — `up -d`는 기존 컨테이너를 내리고 새로 띄우므로 수십 초 끊긴다. 단일 인스턴스에서는 피할 수 없다.
- **롤백 자동화** — 헬스체크 실패 시 이전 이미지로 되돌리는 단계는 없다. `:{sha}` 태그가 남아 있으므로 수동으로는 가능하다.
- **HTTPS / 도메인 / ALB** — 지금은 EC2의 8080을 직접 노출한다. ALB를 붙이면 `/actuator/health`를 타깃 그룹 헬스체크로 연결하고, 그때 compose에도 app healthcheck를 추가한다.
- **DB 백업** — `pgdata`는 EC2 로컬 볼륨 하나뿐이다. 인스턴스를 잃으면 데이터도 잃는다.
- **로그 수집** — 컨테이너 stdout뿐이다.

## 11. 검증

- `./gradlew test` 통과 (신규 actuator 테스트 포함)
- `docker build .` 성공
- `main` 머지 후 GHCR에 `:latest`와 `:{sha}` 태그가 올라옴
- 같은 워크플로 실행이 EC2 배포와 헬스체크까지 초록불로 끝남
- EC2에서 `curl localhost:8080/actuator/health`가 `{"status":"UP"}`
- EC2 외부에서 5432 접속이 거부됨
- 로그인 → `/home` 호출이 배포된 서버에서 정상 동작
