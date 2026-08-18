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
# 코드 전체가 LocalDateTime(오프셋 없음)을 KST 벽시계 값으로 다룬다(CLAUDE.md 참고) — 이 이미지의
# 기본 타임존(UTC)을 그대로 두면 컨테이너의 LocalDateTime.now()가 클라이언트가 보낸 KST 시각보다
# 9시간 뒤처져서, 오전 9시(KST) 이후에 한 러닝이 서버 기준 "아직 안 지난 미래"로 밀려나
# 주간 집계(between weekStart and now)에서 통째로 빠진다. 로컬 개발 OS가 이미 KST라 재현되지 않았다.
ENV TZ=Asia/Seoul
# 1GB 호스트에 postgres/redis가 함께 올라가므로 힙을 컨테이너 메모리의 절반으로 제한한다.
# user.timezone도 함께 지정해 TZ 환경변수 인식 실패 시에도 KST로 고정되게 한다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
