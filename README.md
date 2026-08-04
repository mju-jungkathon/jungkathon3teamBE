# AfterGrow Backend

러닝 트래킹 + 심박수 측정 + AI 회복 가이드 백엔드 API

## 기술 스택

- Java 17 / Spring Boot 4.0.7
- Spring Data JPA / PostgreSQL
- Redis (캐시, 토큰 관리)
- Docker / Docker Compose
- GitHub Actions (CI/CD) → AWS ECS Fargate
- SpringDoc OpenAPI (Swagger UI)

## 로컬 개발 환경 세팅

### 1. 필수 설치

- JDK 17
- Docker Desktop

### 2. 저장소 클론

```bash
git clone https://github.com/mju-jungkathon/jungkathon3teamBE.git
cd jungkathon3teamBE
```

### 3. 환경변수 설정

```bash
cp .env.example .env
```

`.env` 파일을 열어 로컬 값으로 채워주세요. (`.env`는 git에 커밋되지 않습니다)

### 4. application-local.yml 설정

`src/main/resources/application-local.yml` 파일에서 `datasource.username`, `datasource.password`를 `.env`에 적은 값과 동일하게 맞춰주세요. (`application-local.yml`도 git에 커밋되지 않습니다)

### 5. DB / Redis 컨테이너 실행

```bash
docker compose up -d
docker compose ps   # postgres, redis 둘 다 healthy 상태인지 확인
```

### 6. 애플리케이션 실행

IntelliJ에서 `local` 프로파일로 실행하거나:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 7. 정상 확인

- API 서버: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- PostgreSQL: localhost:5432 (DB명/계정은 `.env` 참고)

## 브랜치 전략

| 브랜치 | 용도 |
|---|---|
| `main` | 운영 배포 (자동 배포) |
| `develop` | 개발 통합 |
| `feature/{도메인}-{작업내용}` | 기능 개발 (예: `feature/auth-login`) |

## 커밋 컨벤션

```
feat: 새 기능
fix: 버그 수정
refactor: 리팩토링
test: 테스트 추가/수정
docs: 문서 수정
chore: 빌드/설정 변경
```

## 프로젝트 구조

```
src/main/java/jungkathon3team/aftergrow
├── auth/          # 회원가입/로그인/토큰
├── running/       # 러닝 세션
├── heartrate/     # 심박수 측정
├── recovery/      # 회복 가이드
├── profile/       # 프로필/설정
└── common/        # 공통 응답 포맷, 예외 처리, 시큐리티 설정
```

## 유의사항

- `.env`, `application-local.yml`은 절대 커밋하지 마세요 (`.gitignore`에 이미 포함되어 있습니다)
- 마이그레이션은 Flyway로 관리합니다 (`src/main/resources/db/migration`). 스키마 변경 시 `ddl-auto`로 직접 반영하지 말고 마이그레이션 파일을 작성해주세요

## 문서

- API 명세서 / ERD / 기술 스택: Notion 링크 참고