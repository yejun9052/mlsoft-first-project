# 홈서버 데모 배포용 — 프론트(Vite 빌드) 정적 파일을 백엔드(Spring Boot) static 리소스로
# 그대로 넣어 단일 컨테이너·단일 포트(8080)로 합친다. Tailscale Funnel로 이 포트 하나만
# 공개하면 되고, 프론트·백엔드가 같은 오리진이라 CORS/쿠키 크로스오리진 문제가 아예 생기지
# 않는다(frontend/src/api/index.js가 상대경로 '/api'를 쓰므로 그대로 동작).
#
# 빌드: docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
# (빌드 컨텍스트는 리포 루트 — frontend/, backend/ 둘 다 참조해야 해서 root Dockerfile)

# ---- Stage 1: 프론트 빌드 ----
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- Stage 2: 백엔드 빌드 (프론트 산출물을 static 리소스로 포함시켜 함께 패키징) ----
FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /app/backend
COPY backend/ ./
COPY --from=frontend-build /app/frontend/dist/ ./src/main/resources/static/
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# ---- Stage 3: 런타임 (JDK 아님 — JRE만) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-build /app/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
