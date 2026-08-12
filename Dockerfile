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
