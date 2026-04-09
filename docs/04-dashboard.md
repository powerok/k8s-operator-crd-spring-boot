# 웹 대시보드 (Vue 3 CDN)

## 개요

Quarkus가 정적 리소스와 REST API를 함께 제공합니다. **Vue 3 (CDN)** 단일 페이지로 `operator.example.com` CRD 인스턴스를 조회·YAML 적용·삭제하고, API 테스트 패널을 사용할 수 있습니다.

- UI: `/dashboard/`  
- API: `/api/dashboard/*`  
- 루트 `/` 는 `/dashboard/` 로 안내합니다.

## 실행

```bash
mvn quarkus:dev
```

브라우저에서 `http://localhost:8080/dashboard/` 를 엽니다. kubeconfig가 유효한 클러스터를 가리켜야 합니다.

## API 요약

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/dashboard/health` | 클러스터 연결·버전 |
| GET | `/api/dashboard/kinds` | 5종 CR plural / kind |
| GET | `/api/dashboard/namespaces` | 네임스페이스 목록 |
| GET | `/api/dashboard/resources/{plural}?namespace=` | CR 목록 |
| GET | `/api/dashboard/resources/{plural}/{name}?namespace=` | YAML 본문 |
| DELETE | 동일 | 인스턴스 삭제 |
| POST | `/api/dashboard/apply` | JSON `{ "namespace", "yaml" }` — `createOrReplace` |
| POST | `/api/dashboard/test/ping` | 연결 테스트용 에코 |

## 보안

대시보드 API는 **클러스터 변경이 가능**합니다. 개발·사내망에서만 사용하고, 운영 시에는 인증·TLS·네트워크 정책을 적용하세요. `application.properties`의 CORS는 개발 편의를 위해 넓게 열려 있습니다.
