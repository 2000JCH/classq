# 1. 공통 실행 환경
FROM eclipse-temurin:21-jre AS base
WORKDIR /app
EXPOSE 8080
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    rm -rf /var/lib/apt/lists/* && \
    addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

# 2. 빌드 환경
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# 3. [로컬용] dev - builder에서 빌드한 jar 사용
FROM base AS dev
COPY --from=builder /app/build/libs/classq-0.0.1-SNAPSHOT.jar app.jar
CMD ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]

# 4. [배포용] prod - CI/CD가 보낸 jar 사용
FROM base AS prod
COPY app.jar app.jar
CMD ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
