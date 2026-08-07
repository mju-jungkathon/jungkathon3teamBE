# 📌 프로젝트 컨텍스트 — jungkathon3team (AfterGrow)

> 이 문서는 프로젝트 초기 설계~환경 세팅 과정에서 나온 모든 결정사항과 이유를 정리한 기록입니다. 새 팀원 온보딩이나 "왜 이렇게 했더라?" 싶을 때 여기부터 확인하세요.

---

## 1. 프로젝트 개요

- **앱 이름**: AfterGrow (러닝 트래킹 + 스킨케어 회복 솔루션 앱)
- **컨셉**: 러닝 후 UV 노출·심박수 데이터를 기반으로 AI가 피부/신체 회복 가이드를 제공
- **팀명**: jungkathon3team
- **GitHub Organization**: `mju-jungkathon`
- **레포**: `jungkathon3teamBE` (Private)

---

## 2. 기술 스택 (최종 결정)

| 영역 | 기술 | 결정 이유 |
|---|---|---|
| 언어 | Java 17 | Java 21로 갈 경우 Swagger(springdoc-openapi)에서 호환성 이슈 발생 확인 → 안정성 우선으로 17 유지 |
| 프레임워크 | Spring Boot 4.0.7 | 4.1.x보다 검증 사례 많은 정식 GA 버전 선택 |
| ORM | Spring Data JPA (Hibernate) | ERD 엔티티 관계를 코드로 명확히 표현 가능 |
| DB | PostgreSQL | 관계형 정합성 필요, AWS RDS로 운영 예정 |
| 캐시 | Redis (Spring Data Redis, Access+Driver) | refresh token 관리, 세션 실시간 폴링 캐싱용. Reactive/Session 버전 아님 — MVC 기반이라 표준 버전 선택 |
| 인증 | JWT (Access Token + Refresh Token) | Access는 단명(탈취 피해 최소화), Refresh는 Redis에 저장해 로그아웃 시 즉시 무효화 가능 |
| 마이그레이션 | Flyway | DB 스키마를 버전 관리해 팀원 간 로컬 DB 상태 불일치 방지, 운영 배포 시 예측 가능성 확보 |
| 문서화 | SpringDoc OpenAPI (Swagger) | 3.x 버전 사용 — Spring Boot 4와 호환되는 버전대 |
| 컨테이너 | Docker / Docker Compose | 로컬 개발 환경 통일 (PostgreSQL + Redis) |
| CI | GitHub Actions | PR/push 시 자동 테스트. 배포(ECR/ECS)는 AWS 인프라 준비 후 추가 예정 |
| 배포 인프라 (예정) | AWS (ECS Fargate, RDS, ElastiCache, ALB, VPC, Secrets Manager, CloudWatch) | 아직 미착수 |

---

## 3. 프로젝트 메타데이터

```
Group: jungkathon3team
Artifact: AfterGrow
Package: jungkathon3team.aftergrow   ⚠️ afterglow 아님, aftergrow (오타 주의)
Build: Gradle - Groovy
Packaging: Jar
```

> ⚠️ **자주 헷갈리는 부분**: 앱 이름 표기는 `AfterGrow`/`After Glow` 등 다양하게 썼지만, **실제 코드 패키지명은 `aftergrow`**입니다. 로깅 설정이나 문서 작성 시 `afterglow`로 잘못 쓰지 않도록 주의.

---

## 4. Repo / 협업 규칙

### 브랜치 전략
```
main        ← 운영 배포 (자동 배포 트리거, 보호 규칙: PR 승인 1명 이상 필수)
develop     ← 개발 통합 (PR 필수)
feature/*   ← 기능별 작업 (예: feature/auth-login)
```

### 커밋 컨벤션
```
feat: 새 기능
fix: 버그 수정
refactor: 리팩토링
test: 테스트 추가/수정
docs: 문서 수정
chore: 빌드/설정 변경
```

### 협업 파일
- `.github/PULL_REQUEST_TEMPLATE.md` — PR 작성 시 자동 적용
- `.github/ISSUE_TEMPLATE/bug_report.md` — 버그 이슈 템플릿
- `.github/workflows/test.yml` — PR/push 시 자동 테스트 (JDK 17 기준, PostgreSQL/Redis 서비스 컨테이너 포함)

---

## 5. 로컬 개발 환경 세팅 방법

```bash
git clone https://github.com/mju-jungkathon/jungkathon3teamBE.git
cd jungkathon3teamBE
cp .env.example .env              # 실제 값으로 채우기 (커밋 안 됨)
# src/main/resources/application-local.yml도 직접 생성 후 .env와 동일한 값으로 채우기 (커밋 안 됨)
docker compose up -d              # PostgreSQL + Redis 컨테이너 실행
docker compose ps                 # healthy 상태 확인
./gradlew bootRun --args='--spring.profiles.active=local'
```

- API 서버: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

### 절대 커밋하면 안 되는 파일 (`.gitignore`에 이미 포함됨)
- `.env`
- `src/main/resources/application-local.yml`

---

## 6. 왜 이렇게 설계했는가 — 핵심 의사결정 이유

### Access Token / Refresh Token을 나눈 이유
"로그인 유지"는 사용자에게 보이는 결과일 뿐이고, 그 뒤에서 Access Token은 짧게 만료시켜 탈취 피해를 제한하고 Refresh Token으로 조용히 갱신하는 구조. Refresh Token은 Redis에 저장해 로그아웃 시 즉시 무효화 가능하게 함.

### Redis를 도입한 이유
"서버가 재시작돼도 사라지면 안 되는 데이터"는 PostgreSQL, "사라져도 다시 만들 수 있는 일시적 데이터"는 Redis라는 기준으로 역할 분담:
- PostgreSQL: 러닝 세션 최종 결과, 사용자 정보, 심박수 측정 기록
- Redis: refresh token, 러닝 진행 중 실시간 상태(`/live` 폴링), rate limiting 카운터

### Flyway를 처음부터 도입한 이유
`ddl-auto: update`만 쓰면 팀원마다 로컬 DB 상태가 달라지고, 운영 배포 시 예측 못 한 스키마 변경 위험이 있음. 프로젝트 시작 시점부터 도입하면 이 문제가 원천 차단됨 — 나중에 도입하려면 팀원 간 이미 쌓인 스키마 불일치를 맞추는 게 훨씬 번거로움.

### `.env` / `docker-compose.yml`의 비밀번호 처리 원칙
- `docker-compose.yml`: **커밋되는 파일** → 절대 실제 비밀번호를 직접 적지 않고 `${POSTGRES_PASSWORD}` 같은 변수 참조만 사용
- `.env`: **커밋 안 되는 파일** → 실제 비밀번호는 여기에만 존재
- `.env.example`: **커밋되는 파일** → 값은 비우고 항목 이름(견본)만 제공

---

## 7. 트러블슈팅 기록 (겪었던 문제와 해결)

| 문제 | 원인 | 해결 |
|---|---|---|
| Java 21 사용 시 Swagger 에러 | springdoc-openapi 2.x와 Spring Boot 4 초기 버전 간 Jackson 호환성 이슈 | Java 17 + springdoc-openapi 3.x 조합으로 안정화 |
| `./gradlew build` 시 `contextLoads()` 테스트 실패 (HibernateException) | Docker(PostgreSQL) 컨테이너가 안 뜬 상태에서 테스트가 실제 DB 연결을 시도 | `docker compose up -d`로 DB부터 띄운 뒤 빌드 |
| `docker compose up` 시 Docker API 연결 실패 | Docker Desktop 자체가 안 켜져 있었음 | Docker Desktop 실행 후 재시도 |
| 프로젝트 곳곳에 `aura`(이전 프로젝트명) 잔재 | `docker-compose.yml`의 `container_name`, `.github/workflows/test.yml`의 테스트 DB명에 남아있었음 | 전체 grep으로 검색 후 일괄 `aftergrow`로 치환 |
| `application-local.yml`의 password가 placeholder 텍스트 그대로 남음 | 템플릿 설명 문구를 실제 값으로 착각하고 그대로 붙여넣음 | `.env`와 동일한 실제 값으로 수정 |
| `.github/workflows/test.yml`의 JDK 버전이 21로 남아있었음 | 프로젝트를 17로 결정했는데 워크플로 파일에 반영 안 됨 | JDK 17로 수정 |
| `application-local.yml`의 로깅 패키지가 `afterglow`로 오타 | 실제 패키지명은 `aftergrow` | `aftergrow`로 수정 |

---

## 8. 현재 진행 상황 체크리스트

> 최종 갱신: 2026-08-07 (기준 커밋 `342a7d5 feat: User 엔티티 및 Repository 작성`)

### 환경 세팅

- [x] GitHub Organization 및 Private 레포 생성
- [x] 팀원 초대 (일부 초대 수락 대기 중 — 리마인드 필요)
- [x] Spring Boot 프로젝트 초기화 (Java 17, Spring Boot 4.0.7)
- [x] `.gitignore`, `.env`/`.env.example`, `docker-compose.yml` 세팅
- [x] `application.yml` / `application-local.yml` 분리
- [x] Flyway, Redis, JPA, Security, SpringDoc 의존성 추가
- [x] `.github` 협업 템플릿 (PR, 이슈, 테스트 워크플로) 구성
- [x] 로컬 `./gradlew build` 성공 확인
- [x] 첫 커밋 & 푸시 완료

### DB

- [x] ERD 설계 완료 (Notion에 Mermaid로 공유됨, `docs/ERD_aftergrow.md`)
- [x] Flyway 마이그레이션 **V1~V9 전체 작성** — ERD의 9개 테이블을 모두 덮음 (`d493b9b`)
- [x] Spring Boot 4 Flyway 자동설정 이슈 해결 (`spring-boot-starter-flyway` 추가, `52a7086`)

### 코드

- [x] `User` 엔티티 + `UserRepository` 작성 (`342a7d5`)
- [ ] `common/` 공통 모듈 — `ApiResponse<T>`, 에러코드 enum, `@ControllerAdvice`, `RedisConfig`
- [ ] **`SecurityConfig`** — 현재 Security 스타터만 올라가 있고 설정 클래스가 없어서, Swagger UI 포함 모든 엔드포인트가 자동 생성 Basic 인증에 막혀 있음
- [ ] 회원가입/로그인 API 구현
- [ ] JWT 발급/검증 로직 구현 (`application-local.yml`에 `jwt.*` 설정 키는 미리 잡아둠, 읽는 코드는 아직 없음)
- [ ] 나머지 도메인(러닝, 심박수, 회복가이드, 프로필) 엔티티 및 API 순차 구현
      — 스키마는 다 있고 자바 코드만 `users`까지 와 있는 상태

### 테스트 / 인프라

- [ ] 테스트 — 현재 `AftergrowApplicationTests.contextLoads()` 하나뿐. `application-test.yml`이 없어서 CI는 환경변수로만 주입 중
- [ ] `develop` 브랜치 생성 — **아직 로컬/origin 모두 없음.** 문서상 전략은 `feature/*` → `develop` → `main`이지만 실제로는 PR #1이 `feature/db-migration` → `main`으로 직행했음
- [ ] 브랜치 보호 규칙 설정 (`develop` 생성 후 함께)
- [ ] AWS 인프라 구성 및 배포 파이프라인 연결

### 문서

- [x] 프로젝트 컨텍스트 / ERD / 기술스택 / 개발시작가이드 작성
- [x] `CLAUDE.md` 작성 (Claude Code용 저장소 가이드)
- [ ] `docs/` 커밋 — 현재 untracked 상태라 클론한 팀원에게는 보이지 않음

---

## 9. 개발 순서 (권장 로드맵)

화면 순서가 아니라 **의존성 낮은 것부터** 진행:

1. 공통 응답 포맷, 예외 처리, Redis 설정
2. User 엔티티 + 회원가입/로그인 + JWT (막히면 이후 전체 API가 막히므로 최우선)
3. 프로필/목표/알림 설정 (1:1 관계 연습)
4. 러닝 세션 시작/종료
5. 심박수 측정 (워치/rPPG)
6. 회복 가이드 생성
7. 측정 기록 목록/재측정
8. 홈 대시보드 (다른 도메인 데이터를 종합하므로 마지막)

---

## 10. 관련 문서 (Notion 내 링크로 대체 예정)

- API 명세서 (화면 1~9, 엔드포인트 전체)
- ERD (Mermaid 다이어그램)
- 백엔드 기술 스택 문서
- 개발 시작 가이드

---

## 11. 참고하면 좋은 학습 자료

- Docker 공식 "Get Started" 튜토리얼
- The Twelve-Factor App (12factor.net)
- AWS 공식 ECS Workshop
- Spring Boot 공식 가이드 "Spring Boot with Docker"
- springdoc-openapi 공식 문서 (Spring Boot 4 호환 버전 확인용)
- JWT.io — 토큰 구조 디코딩 실습
- OWASP JWT Cheat Sheet
