# SupaLeague: No-Mod 마인크래프트 축구 플랫폼 아키텍처

## 1. 프로젝트 개요
- **목적**: 클라이언트 모드 설치 없이(바닐라 1.20+ 호환) 즐길 수 있는 고성능 마인크래프트 축구 플랫폼 구축.
- **인프라/DevSecOps 목표**: 프록시 기반 트래픽 보호, 동적 컨테이너 오케스트레이션, 넷코드 최적화 및 관측성(Observability) 확보를 통한 실전 인프라 엔지니어링 포트폴리오 구축.
- **주요 언어 및 스택**: Kotlin (JVM), Paper 1.20.4+, Velocity, Docker Engine API, Redis 7.x, Prometheus & Grafana.

---

## 2. Phase별 핵심 명세서 인덱스

| 문서 | 핵심 주제 | 세부 요약 |
|---|---|---|
| **[Phase 1: 인프라 & 라이프사이클](file:///c:/work/supaleague/docs/architecture/phase-1-infrastructure.md)** | 인스턴스 스케일링, 프록시, 격리 | Velocity 라우팅, Docker 브리지(`supa-net`), 웜 풀(Warm Pool), 지연 바인딩, 소프트 리셋, 릴레이(Relay) 관중 격리 |
| **[Phase 2: 물리 엔진 & 조작계](file:///c:/work/supaleague/docs/architecture/phase-2-physics-and-controls.md)** | 볼 탄도학, 넷코드, 온/오프더볼 | `Display`+`Interaction` 듀얼 엔티티, 100Hz 물리 서브스텝, 마그누스 스핀, 감아차기, 수동 궤적 가이드 |
| **[Phase 3: 스탯, 포지션 & 육성](file:///c:/work/supaleague/docs/architecture/phase-3-stats-and-positions.md)** | 능력치 모델, 스태미나, UI | 1:1 단일 스탯, 듀얼 스태미나 엔진, 스탯 총합 예산(520pt), 대립 스탯 시소 제약, 조건부 특성 풀, BetterHUD |
| **[Phase 4: DevSecOps & 관측성](file:///c:/work/supaleague/docs/architecture/phase-4-devsecops-and-monitoring.md)** | CI/CD, 모니터링, 보안 하드닝 | 순수 도메인 단위 테스트, Trivy CVE 스캔, Prometheus 동적 SD, Grafana MSPT 모니터링, Velocity Modern Forwarding, nftables L4 방화벽 |

---

## 3. 거시적 아키텍처 트레이드오프 종합 점검

### 3.1 인프라: 분산 컨테이너 vs 단일 모놀리식 서버
- **결정**: Velocity + Orchestrator 기반 Docker 동적 컨테이너 격리.
- **트레이드오프**:
  - *비용/복잡도 증가*: 분산 세션 관리, 오케스트레이터 프로세스 상주, 인스턴스당 JVM 메모리 오버헤드.
  - *이점 (선택 이유)*: 특정 아레나의 틱 드랍(MSPT 지연)이나 비정상 크래시가 전체 서버로 전파되지 않는 물리적 격리(cgroups) 달성. DevSecOps 포트폴리오의 실질적 가점 요소.
  - *완화책*: 웜 풀(Warm Pool) 사전 기동으로 콜드 스타트 제거, 512MB~768MB 수준의 극단적 Paper 서버 다이어트.

### 3.2 클라이언트 환경: No-Mod (바닐라 호환) vs 커스텀 모드(Fabric/Forge)
- **결정**: 클라이언트 모드 설치 배제 (100% 바닐라 클라이언트 호환).
- **트레이드오프**:
  - *표현력/입력 한계*: 마인크래프트 기본 입력(좌/우클릭, Q, F, Shift)의 한정성, 커스텀 UI 렌더링의 제약.
  - *이점 (선택 이유)*: 유저 접근성 극대화 (모드팩/런처 설치 허들 제로).
  - *완화책*: BetterHUD(음수 간격 폰트 리소스팩)를 통한 네이티브급 UI 렌더링, `ItemDisplay`의 클라이언트 60+ FPS 보간 활용.

### 3.3 게임 루프: 20 TPS 서버 틱 유지 vs 물리 서브스텝 (Sub-stepping)
- **결정**: 서버 틱은 20 TPS(50ms) 유지 + 내부 물리 루프만 100Hz(10ms) 분할 적분.
- **트레이드오프**:
  - *서버 틱 강제 상향 시 결함*: 클라이언트 내장 20 TPS 타이머와 엇갈려 고무줄 현상(Desync) 및 2.5배속 가속 버그 발생.
  - *선택 이유*: 공 1개에 대한 수 마이크로초($\mu s$) 수준의 가벼운 부동소수점 연산으로 터널링 방지와 마그누스 스핀 궤적 정밀도를 완벽 확보.

### 3.4 캐릭터 밸런싱: 자유 육성 vs 수학적 상충 제약 (Seesaw)
- **결정**: 스탯 총합 상한(520pt) + 주력/몸싸움 대립 합계 제약($\le 155$).
- **트레이드오프**:
  - *자유도 제한*: 모든 능력치를 99로 만드는 이른바 '육각형 괴물' 빌드 차단.
  - *선택 이유*: 체형(키/몸무게) 설정이 불가능한 마인크래프트 특성을 보완하고, 축구 게임의 고질적 문제인 '속도 올인 메타(Pace Meta)' 붕괴 방어.
