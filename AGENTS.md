# SupaLeague Agent Governance & Architecture Invariants

## 1. 프로젝트 정체성 & 엔지니어링 목표
- **시스템 정의**: No-Mod 마인크래프트 클라이언트(1.20.4+)를 수용하는 고성능 분산 인프라 및 실시간 물리 시뮬레이션 플랫폼.
- **핵심 엔지니어링 포커스**:
  - **DevSecOps & 클라우드 인프라**: Docker Engine API 기반 동적 인스턴스 오케스트레이션, 웜 풀(Warm Pool) 수명 주기 관리, cgroups 자원 격리.
  - **네트워크 & 인프라 보안**: Velocity 프록시 Modern Forwarding(HMAC-SHA256), L4 방화벽(nftables), 무인가 백엔드 다이렉트 접근 원천 차단.
  - **관측성(Observability)**: Prometheus 파일 기반 서비스 디스커버리(File SD), JVM GC/MSPT 메트릭 수집 및 Grafana 대시보드 연동.
  - **실시간 시스템 최적화**: 20 TPS 서버 틱과 독립된 100Hz 물리 서브스텝 연산, 넷코드 보간.

---

## 2. 아키텍처 불변식 (Hard Invariants)
에이전트는 다음 경계를 침범하는 코드를 작성할 수 없다:
1. **클라이언트 모드 배제**: Forge/Fabric/커스텀 패킷 의존 코드 작성 금지. 100% 바닐라 패킷/엔티티 메커니즘(`Display`, `Interaction`, 리소스팩)만 사용.
2. **도메인 순수성 보장**:
   - `core/physics`, `core/stats` 등 핵심 연산 모듈은 Bukkit/Spigot/Paper API 의존성을 일절 포함하지 않는 순수 Kotlin/JVM 모듈이어야 한다.
   - 마인크래프트 엔진 API는 오직 어댑터 레이어(`apps/arena-server`)에서만 소비한다.
3. **네트워크 격리 원칙**:
   - 경기 인스턴스(Arena Server)는 외부 인터넷에 포트를 노출하지 않으며, 전용 Docker 네트워크(`supa-net`) 내부에서만 통신한다.
   - 모든 유저 세션은 프록시(`Velocity`)를 경유해야 하며, 위조 패킷 방지를 위해 전달 모드 검증 실패 시 즉시 소켓을 드롭한다.
4. **컨테이너 태깅 불변성**:
   - `:latest` 태그 사용 금지. Git Commit SHA 및 SemVer 기반 고유 태그만 허용한다.

---

## 3. 엔지니어링 트레이드오프 및 의사결정 기록 규격
- 기능 구현 및 인프라 변경 시 단순 구현에 그치지 않고, 반드시 다음 항목을 PR/문서에 명시한다:
  - **선택한 아키텍처와 탈락한 대안** (예: 모놀리식 단일 서버 vs 동적 컨테이너).
  - **발생 가능한 실패 모드 최소 2가지** (예: cgroups OOMKilled 발생 시 좀비 세션 처리, 프록시-백엔드 간 Keep-Alive 타임아웃).
  - **보안 위협 분석** (STRIDE 모델 기반 공격 벡터 및 방어 기제).

---

## 4. 모노레포 구조 및 참조 가이드
- 상세 워크플로우 및 CI/CD 규칙: [workflow-rules.md](file:///c:/work/supaleague/docs/governance/workflow-rules.md)
- 아키텍처 세부 명세: [README.md](file:///c:/work/supaleague/docs/architecture/README.md)
- 디렉토리 구성:
```text
supaleague/
├── AGENTS.md                  # 루트 거버넌스 및 불변식 (본 문서)
├── docs/                      # 아키텍처 및 거버넌스 문서
├── apps/
│   ├── proxy/                 # Velocity 프록시 플러그인 및 포워딩 설정
│   ├── orchestrator/          # Docker Engine API 기반 인스턴스 라이프사이클 데몬
│   └── arena-server/          # Paper 기반 경기장 전용 마이크로서버 플러그인
├── core/
│   ├── physics/               # 순수 100Hz 서브스텝 탄도학/충돌 엔진 (순수 JVM)
│   └── stats/                 # 1:1 스탯 시소 제약 및 스태미나 도메인 로직
└── infra/
    ├── docker/                # 컨테이너 베이스 이미지 및 배포 정의
    ├── network/               # nftables 방화벽 규칙 및 브리지 네트워크 스크립트
    └── observability/         # Prometheus SD 타겟 생성기 및 Grafana 템플릿
```
