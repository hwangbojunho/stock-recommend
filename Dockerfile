# 1. 프론트엔드 빌드
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. 백엔드 빌드 (프론트 빌드 결과를 Spring Boot 정적 리소스로 포함)
FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /app/backend
COPY backend/ ./
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
RUN ./gradlew bootJar -x test --no-daemon

# 3. 실행 이미지
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-build /app/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

