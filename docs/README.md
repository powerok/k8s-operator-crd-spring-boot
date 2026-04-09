# 개발 산출물 문서 목차

본 디렉터리는 **k8s-operator** 프로젝트의 설계·구현·검증을 항목별로 나눈 개발 산출물입니다.  
다이어그램은 **Mermaid**로 작성했으며, **주제별로 별도 파일**로 관리합니다.

| 문서 | 설명 |
|------|------|
| [아키텍처 개요](01-architecture.md) | 시스템 구성, CRD·Reconciler 관계, 기술 스택 |
| [개발 환경](02-development-environment.md) | JDK, Maven, 클러스터, Docker Compose |
| [빌드 및 배포](03-build-and-deploy.md) | 패키징, CRD 적용, 이미지, 운영 실행 |
| [웹 대시보드](04-dashboard.md) | Vue 3 CDN UI · CRD 조회·YAML 적용·API 테스트 |
| [CRD 개발 가이드](05-cr-development-guide.md) | CRD 구축 및 Reconciler 연동 절차 상세 |
| [MaintenanceWindow](06-maintenance-window.md) | 업무 시간대 기준 Replica 조정 |
| [GhostDatabase](07-ghostdatabase.md) | PostgreSQL 자동 프로비저닝 CRD·조정 흐름 |
| [ResourceQuotaGuard](08-resource-quota-guard.md) | 쿼터 임계치 감시 및 스케일 다운 |
| [StaticSiteDeployer](09-static-site-deployer.md) | Git + Nginx 정적 사이트 배포 |
| [ApiGatewayRoute](10-api-gateway-route.md) | Ingress 기반 API 라우트·인증 어노테이션 |
| [테스트 및 검증](11-testing-and-verification.md) | 단위 테스트, 통합 검증, 관측 |

프로젝트 루트: `k8s-operator/`  
API 그룹: `operator.example.com/v1alpha1`
