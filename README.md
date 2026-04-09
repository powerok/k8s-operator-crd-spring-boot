# K8s Operator (Quarkus-based)

본 프로젝트는 [Quarkus Operator SDK](https://quarkus.io/guides/operator-sdk)를 기반으로 작성된 쿠버네티스 오퍼레이터(Kubernetes Operator) 환경입니다. Docker Compose와 k3s를 연동하여 로컬 환경에서 신속하게 개발 및 테스트할 수 있도록 설계되었습니다.

## 주요 기능 및 구성요소
이 오퍼레이터는 다수의 Custom Resource Definition(CRD)을 내장하고 있으며, 각 리소스를 자동으로 제어하는 Reconciler 컨트롤러가 포함되어 있습니다.

1. **GhostDatabase:** Postgres 데이터베이스, PVC, 서비스 등을 자동 프로비저닝
2. **MaintenanceWindow:** 특정 시간대에 Deployment/StatefulSet의 Replicas를 조절하여 인프라 비용 절감
3. **ResourceQuotaGuard:** 워크스페이스나 네임스페이스의 Quota를 모니터링하고 초과 시 제한/경고
4. **StaticSiteDeployer:** Git 저장소의 정적 사이트(HTML/JS)를 컨테이너로 패키징해 배포
5. **ApiGatewayRoute:** Kong/Istio 등의 API Gateway에 특화된 동적 라우팅 설정
6. **대시보드 (Dashboard):** 로컬 접근 가능한 REST API와 웹 UI를 통해 현재 상태와 커스텀 리소스를 모니터링/적용 가능

## 환경 설정 및 빌드

사전 요구사항: 
- Docker 및 Docker Compose
- Java 17 이상, Maven
- WSL2 (Windows 환경일 경우)

### 로컬 환경 시작 (Docker Compose)
가장 빠르고 간편하게 테스트하는 방법은 준비된 `docker-compose.yml`을 이용하는 것입니다. 
K3s 클러스터와 Operator 컨테이너가 함께 구동됩니다.

```bash
# 컨테이너 빌드 및 백그라운드 실행
docker compose up -d --build
```
> **참고:** 오퍼레이터 시작 시(startup), 빌드 산출물 중 CRD 매니페스트(`*-v1.yml`)가 자동으로 k3s 클러스터에 반영됩니다.

### 대시보드 접근 (Dashboard UI)
로컬 호스트의 `8080` 포트로 오퍼레이터 대시보드에 접근할 수 있습니다. 해당 UI를 통해 손쉽게 YAML 매니페스트를 클러스터에 적용(Apply)해 볼 수 있습니다.
👉 http://localhost:8080/dashboard/

## 디렉토리 구조 

- `/src/main/java/.../` : 오퍼레이터 소스코드 핵심 로직 (CR, Spec, Status, Reconciler)
- `/src/main/resources/` : 대시보드 정적 파일(`META-INF/resources/dashboard`), 오퍼레이터 설정 방침 (`application.properties`)
- `/docs/` : 리소스별 동작 방식, 개발 가이드 등 세부 공식 문서
- `/docker-compose.yml` : Dev(개발) 및 테스트용 로컬 클러스터 셋업 

## 문서화 (Documentation)
오퍼레이터의 상세 스펙과 CRD/Reconciler 개발 가이드라인은 `/docs/` 디렉토리를 참조하세요.
- [아키텍처 개요 (Architecture)](docs/01-architecture.md)
- [의존성 & 환경 구성 상세 (Build & Deploy)](docs/03-build-and-deploy.md)
- [CRD & Reconciler 개발 절차서 (CRS 개발 가이드)](docs/05-cr-development-guide.md)

---
*Powered by [Quarkus 3.33.1](https://quarkus.io/) & [Fabric8 Kubernetes Client](https://fabric8.io/)*
