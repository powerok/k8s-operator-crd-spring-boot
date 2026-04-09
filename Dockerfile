# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /project

# 종속성 캐싱을 위해 pom.xml 먼저 복사
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 소스 복사 및 패키징
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app

# kubectl 설치 (CRD 사전 적용용)
RUN apt-get update && apt-get install -y curl && \
    curl -LO "https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" && \
    chmod +x kubectl && mv kubectl /usr/local/bin/kubectl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Quarkus 앱 파일 복사
COPY --from=build /project/target/quarkus-app/ /app/

# CRD yaml 복사 (빌드 시 target/kubernetes/에 생성됨)
COPY --from=build /project/target/kubernetes/ /app/kubernetes/

# 환경 변수 기본값 (필요시 docker-compose에서 오버라이드)
ENV QUARKUS_HTTP_PORT=8080
ENV QUARKUS_KUBERNETES_CLIENT_TRUST_CERTS=true

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
