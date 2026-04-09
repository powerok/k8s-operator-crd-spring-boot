## 개발 구현 요청서: Quarkus 기반 Kubernetes CRD 컨트롤러 개발

본 요청서는 **WSL2** 및 **Docker Compose** 환경에서 **Quarkus** 프레임워크를 활용하여 **Kubernetes Custom Resource Definitions (CRD)**를 개발하고 테스트하기 위한 가이드를 제공합니다.

---

### 1. 개발 환경 구성
* **OS:** Windows 10/11 (WSL2 - Ubuntu 추천)
* **Runtime:** Docker Desktop (WSL2 Backend 활성화)
* **Framework:** Quarkus (Quarkus Operator SDK 익스텐션 포함)
* **Local K8s:** Docker Compose를 이용한 **Kind** 또는 **k3d** 클러스터 구성
* **Language:** Java 17+ / Kotlin

---

### 2. 프로젝트 핵심 아키텍처
Quarkus의 **Operator SDK**를 사용하여 CRD의 생명주기(Reconciliation)를 관리합니다. Docker Compose는 로컬에서 쿠버네티스 클러스터를 모의하거나, 컨트롤러가 참조할 외부 인프라(DB, Redis 등)를 띄우는 용도로 사용합니다.



---

### 3. 실용적인 CRD 및 컨트롤러 선정 (5종)

프로젝트의 직관성과 실용성을 고려하여 아래 5가지 CRD 구현을 제안합니다.

| 서비스 명칭 | 목적 및 기능 |
| :--- | :--- |
| **1. GhostDatabase** | 개발자가 YAML에 DB 명칭과 용량만 적으면, 컨트롤러가 자동으로 PostgreSQL Pod와 Service, PVC를 생성하고 초기 스키마를 구성함. |
| **2. StaticSiteDeployer** | Git Repo URL을 입력하면, 컨트롤러가 Nginx Pod를 띄우고 소스 코드를 동기화하여 정적 웹사이트를 즉시 배포함. |
| **3. ResourceQuotaGuard** | 특정 네임스페이스의 자원 사용량이 80%를 넘을 때 관리자에게 알림을 보내거나 자동으로 특정 라벨이 붙은 워크로드를 스케일 다운함. |
| **4. MaintenanceWindow** | 특정 시간에만 애플리케이션을 가동하고, 업무 시간 외에는 복제본(Replicas)을 0으로 줄여 비용을 절감하는 스케줄러 기반 CRD. |
| **5. ApiGatewayRoute** | 새로운 API 엔드포인트를 정의하면, 기존 API Gateway(Ingress 등)의 설정을 자동으로 업데이트하고 인증 정책을 적용함. |

---

### 4. 주요 구현 단계

#### Step 1: 프로젝트 초기화
```bash
quarkus create app com.example:k8s-operator \
    --extension=kubernetes-operator-sdk,container-image-docker
```

#### Step 2: CRD 모델 정의 (Java)
`CustomResource` 클래스를 상속받아 `Spec`과 `Status`를 정의합니다. (예: `GhostDatabase.java`)

#### Step 3: Reconciler 로직 작성
리소스의 생성/수정/삭제 이벤트가 발생했을 때 수행할 동작을 구현합니다.
* `reconcile()` 메소드 내에서 Kubernetes 클라이언트(Fabric8)를 사용하여 하위 리소스 생성.

#### Step 4: Docker Compose 기반 로컬 테스트 환경
`docker-compose.yml`을 통해 로컬 K8s 클러스터(Kind)를 실행합니다.
```yaml
services:
  kind:
    image: kindest/node:v1.27.3
    privileged: true
    ports:
      - "6443:6443"
```

---

### 5. 테스트 및 검증 방안
1.  **Unit Test:** `QuarkusTest`를 활용하여 Reconciler 로직 검증.
2.  **Integration Test:** WSL2 환경에서 `kubectl apply -f crd.yaml` 실행 후, 실제 Pod가 의도한 대로 생성되는지 관찰.
3.  **Observability:** Quarkus의 Dev UI를 통해 Operator의 상태와 이벤트를 실시간 모니터링.

이 요청서를 바탕으로 구체적인 코딩 컨벤션과 배포 파이프라인을 추가하여 개발을 진행하시기 바랍니다.