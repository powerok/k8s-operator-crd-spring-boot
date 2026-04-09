# Custom Resource (CR) 및 Reconciler 개발 절차서 (CRS 개발 가이드)

Quarkus Operator SDK를 활용하여 새로운 Custom Resource(CR)와 이를 제어(Reconcile)하는 컨트롤러(Reconciler)를 프로젝트에 추가하는 전 과정을 단계별로 상세하게 기술한 문서입니다.

---

## 단계 요약 (Overview)

새로운 커스텀 리소스 기반의 연동을 추가하려면 다음 **4가지 핵심 단계**를 수행해야 합니다.

1. **도메인 모델 스키마 설계**: API Group, Version, Kind 정의
2. **CR 모델 클래스 생성**: `CustomResource`, `Spec`, `Status` Java 클래스 생성 
3. **Reconciler 로직 구현**: 실제 인프라 및 쿠버네티스 자원 제어 로직 작성
4. **대시보드 / 권한(RBAC) 등 추가 스펙 통합**: 권한 설정 및 모니터링 반영

---

## 단계별 개발 절차 상세

### 1단계. 도메인 스키마 및 그룹/버전 설계

가장 먼저 생성할 Custom Resource의 K8s 명세(GVK - Group, Version, Kind)를 నిర్ణ정해야 합니다.
본 프로젝트에서는 기본 그룹으로 `operator.example.com`을 활용하고 있습니다.

- **Group (그룹)**: `operator.example.com`
- **Version (버전)**: `v1alpha1`
- **Kind (종류)**: (예시) `MyCustomService`

---

### 2단계. Java 모델 클래스 구현 (Spec, Status 및 CR 래퍼)

Quarkus Operator SDK는 Java 클래스를 매핑하여 자동으로 CRD(`.yml`) 매니페스트를 생성합니다. 
새 모듈 패키지(`com.example.k8soperator.mycustom`)를 생성하고 3개의 클래스를 작성합니다.

#### 1) Spec 클래스 생성 (`MyCustomServiceSpec.java`)
사용자가 적용(Apply)할 때 작성될 `spec:` 하위 구문을 정의합니다.

```java
package com.example.k8soperator.mycustom;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyCustomServiceSpec {
    private String deploymentName;
    private int customReplicas;

    // Getter & Setter 필수
    public String getDeploymentName() { return deploymentName; }
    public void setDeploymentName(String deploymentName) { this.deploymentName = deploymentName; }
    
    public int getCustomReplicas() { return customReplicas; }
    public void setCustomReplicas(int customReplicas) { this.customReplicas = customReplicas; }
}
```

#### 2) Status 클래스 생성 (`MyCustomServiceStatus.java`)
클러스터에 반영된 상태 및 결과를 나타낼 `status:` 객체입니다.

```java
package com.example.k8soperator.mycustom;

public class MyCustomServiceStatus {
    private String phase;
    private String message;

    // Getter & Setter
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
```

#### 3) CustomResource 래퍼 클래스 (`MyCustomService.java`)
실제 API Group과 Version, Kind가 지정되는 최상단 객체입니다. `CustomResource`를 상속(extends) 받습니다.

```java
package com.example.k8soperator.mycustom;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("operator.example.com")
@Version("v1alpha1")
public class MyCustomService 
    extends CustomResource<MyCustomServiceSpec, MyCustomServiceStatus> 
    implements Namespaced {
    // 본문은 비워두거나 헬퍼 메서드를 추가할 수 있습니다.
}
```

---

### 3단계. 컨트롤러(Reconciler) 로직 구현

Operator가 CRD를 감시하고, 변동이 생겼을 때 처리(Reconcile)를 수행할 Controller 코드를 작성합니다.

```java
package com.example.k8soperator.mycustom;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class MyCustomServiceReconciler implements Reconciler<MyCustomService> {

    private final KubernetesClient client;

    public MyCustomServiceReconciler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<MyCustomService> reconcile(MyCustomService resource, Context<MyCustomService> context) {
        MyCustomServiceSpec spec = resource.getSpec();
        String targetDep = spec.getDeploymentName();
        String ns = resource.getMetadata().getNamespace();

        // 1. 목표 리소스(예: Deployment) 검색
        Deployment dep = client.apps().deployments().inNamespace(ns).withName(targetDep).get();
        
        if (dep == null) {
            // 자원이 없다면 Status 상태를 갱신하고 종료
            return updateStatus(resource, "Failed", "Target deployment not found");
        }

        // 2. 비즈니스 로직 적용 (Replicas 변경 등)
        if (dep.getSpec().getReplicas() != spec.getCustomReplicas()) {
            dep.getSpec().setReplicas(spec.getCustomReplicas());
            client.apps().deployments().inNamespace(ns).resource(dep).update();
        }

        // 3. 작업이 완료되었다면 Status에 성공 메시지 갱신 및 완료 반환
        return updateStatus(resource, "Ready", "Successfully synced");
    }

    private UpdateControl<MyCustomService> updateStatus(MyCustomService cr, String phase, String msg) {
        if (cr.getStatus() == null) cr.setStatus(new MyCustomServiceStatus());
        cr.getStatus().setPhase(phase);
        cr.getStatus().setMessage(msg);
        return UpdateControl.patchStatus(cr);
    }
}
```
**참고사항:** 
* Controller 작성 시 `@ControllerConfiguration` 애노테이션을 부착해야 Quarkus 앱 시작 시점에 스캐닝하여 자동 실행됩니다.
* 의존하는 추가 Kubenetes 자원이 있다면 `Context<T>` 모델을 이용하여 `EventSource` (Informer) 등록 및 폴링을 제어할 수 있습니다.

---

### 4단계. 빌드 타임 검증 및 대시보드 반영

#### 4-1. 빌드 및 매니페스트 확인
`mvn clean package` 커맨드를 실행하면 자동으로 `target/kubernetes` 경로 하위에 다음 매니페스트들이 도출됩니다.
- `mycustomservices.operator.example.com-v1.yml` (CRD 정의 파일)
- `kubernetes.yml` 안에 자동 생성된 `ClusterRole` 통계 

해당 CRD 파일은 프로젝트의 자동 배포 시스템(`docker-compose.yml` 등)을 통해 컨테이너 시작 전 쿠버네티스 클러스터로 자동 Apply 됩니다.

#### 4-2. 역할 및 권한 (RBAC) 확인
만약 `Reconciler` 상에서 `Pod`, `Service`, `Deployment` 등의 쿠버네티스 리소스를 조작해야 한다면, 쿠버네티스 보안 설정 상 권한을 열어주어야 합니다.
Quarkus 환경에서는 보통 코드를 분석해 자동 할당하지만, 추가 권한이 필요할 시 애플리케이션 프로퍼티 (`application.properties`)를 확인하세요.

#### 4-3. 대시보드에 신규 CR 노출 통보 (`DashboardResource.java`)
현재 이 오퍼레이터 안에는 훌륭한 UI 대시보드가 내장되어 있습니다. 신규 CR이 생겼다면 대시보드의 목록 리스트업을 위해 `DashboardResource.java` 파일 내 `kinds()` 메소드를 열어 신규 클래스를 추가합니다.

```java
  @GET
  @Path("/kinds")
  public List<CrdKind> kinds() {
    return List.of(
        // ... (기존 목록)
        new CrdKind("mycustomservices", "MyCustomService", "나만의 커스텀 제어용 리소스") // 추가 부분
    );
  }
```

---

## 트러블슈팅 가이드

* **Q. 컨테이너 기동 시 "ImagePullBackOff" 현상이 발생합니다.**
  > `operator` 서비스는 `docker-compose` 밖이 아닌 동일 네트워크 대역에서 별도로 실행됩니다. 쿠버네티스 내부에 또 다시 오퍼레이터를 띄우려 하지 않도록 `docker-compose.yml` 배포 루프에서 CRD(`*-v1.yml`)만 반영하게 세팅되어 있는지 확인하세요.
* **Q. 대시보드 YAML 적용(Apply)에서 400 에러("유효한 리소스를 찾을 수 없습니다")가 발생합니다.**
  > YAML에서 `apiVersion`, `kind`를 정확하게 적었는지 점검하세요. Jackson 구문 오류가 뜨지 않게 들여쓰기를 확인하십시오. Dashboard 백엔드는 이제 범용 파서를 쓰기 때문에 오타가 있으면 무효화 처리됩니다.
* **Q. Reconciler 안에서 권한 문제(Forbidden)가 발생합니다.**
  > RBAC 규칙 생성이 누락되었을 수 있습니다. Reconciler 윗단에 `@ControllerConfiguration`이 잘 부착되었는지 검토 후 Rebuild (`mvn package`)를 수행하십시오. 
