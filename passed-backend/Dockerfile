FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

# 의존성 캐시 활용
RUN ./gradlew dependencies --no-daemon || true

COPY src src

RUN ./gradlew clean bootJar --no-daemon


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/build/libs/app.jar app.jar

USER spring:spring

EXPOSE 8080

HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=30s \
    --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
