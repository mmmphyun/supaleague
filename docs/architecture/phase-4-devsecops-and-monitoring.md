# Phase 4: DevSecOps 파이프라인, 관측성 및 보안 하드닝 명세서

## 1. CI/CD 파이프라인 아키텍처 (GitHub Actions)

### 1.1 빌드 및 테스트 자동화 (Multi-Layer Testing)
- **Layer 1: 순수 도메인 단위 테스트 (JUnit 5)**
  - 마인크래프트 Bukkit API 의존성이 없는 순수 Kotlin 수학/로직 클래스(`Vector3D`, 물리 서브스텝, 듀얼 스태미나 감쇠, 특성 자격 검증).
  - 실행 시간 수 초 이내로 100% 정밀 검증.
- **Layer 2: 런타임 스모크 테스트 (Testcontainers)**
  - 실제 경량 Paper 서버 컨테이너를 CI 환경에서 15초간 부팅.
  - 플러그인 클래스로딩 및 의존성 주입 이상 유무 검증.

### 1.2 보안 정적 분석 및 컨테이너 취약점 검증 (Trivy)
- **SAST / Secret Scan**: 소스코드 내 하드코딩된 시크릿 및 고위험 취약점 검출.
- **Container Scan**: 빌드된 Docker 이미지(`ghcr.io/.../arena:latest`)의 베이스 OS 패키지 스캔.
  - `CRITICAL` 심각도 발견 시 빌드 파이프라인 즉각 차단(Fail-fast).
- **런타임 베이스 이미지**: `eclipse-temurin:21-jre-jammy` (Ubuntu 기반) 채택하여 Netty epoll 네이티브 가속 및 glibc 호환성 보장. Non-root(`USER 10001`) 계정 강제.

---

## 2. 관측성(Observability) 스택 (Prometheus + Grafana + Spark)

### 2.1 메트릭 수집 지표
1. **틱 안정성**:
   - `minecraft_server_tps`: 초당 틱 (20.0 기준).
   - `minecraft_server_mspt`: 틱당 소요 시간 (45ms 초과 시 경보).
   - `supaleague_physics_substep_duration_nanos`: 100Hz 볼 물리 연산 시간.
2. **동적 컨테이너 스크랩 (Service Discovery)**:
   - Prometheus `docker_sd_configs`를 통해 생성/파기되는 아레나 컨테이너를 실시간 감지하여 타겟 누수 방지.
3. **병목 프로파일링 (Spark)**:
   - MSPT 45ms 이상 지속 시 자동으로 CPU 샘플링 프로파일러를 트리거하고 슬랙/디스코드 웹훅 전송.

---

## 3. L4/L7 보안 하드닝 및 인프라 격리

### 3.1 Velocity Modern Forwarding (HMAC-SHA256)
- BungeeCord의 취약한 텍스트 기반 IP 포워딩 배제.
- `LoginPluginMessage` 패킷을 통해 클라이언트 UUID/IP 데이터를 공유 시크릿 키로 HMAC-SHA256 서명.
- 백엔드 Paper 서버는 서명이 검증된 트래픽만 허용하며 위조 접속을 원천 차단.

### 3.2 방화벽 및 네트워크 격리 (nftables)
- **공인 인터페이스 (`eth0`)**: 오직 `TCP 25565`(Velocity), `TCP 22`(관리자 화이트리스트 SSH), `TCP 443`(Grafana)만 인바운드 허용.
- **DOCKER-USER 체인 하드닝**: 외부에서 백엔드 아레나 컨테이너 가상 IP(`172.20.0.X`), Redis, Docker Daemon 소켓으로의 비인가 직접 인입 차단.
- **커널 튜닝**: `tcp_syncookies = 1`, `tcp_max_syn_backlog = 8192`, `nf_conntrack_max = 262144` 적용으로 L4 SYN Flood 및 포트 고갈 완화.

---

## 4. 무중단 배포 및 유지보수 정책
- **서버 책임 분리**:
  - `Lobby Server`: 상시 24/7 구동 (게임플레이 플러그인 미탑재).
  - `Training Server`: 훈련 전용 분리.
  - `Arena Server`: 웜 풀 기반 롤링 교체. 진행 중인 경기는 보존하고 대기 컨테이너만 최신 이미지로 스왑.
- **정기 점검 윈도우**: 대규모 메이저 패치는 심야 오프피크(04:00~06:00) 시간대에 사전 공지 후 일괄 처리.

---

## 5. 핵심 설계 트레이드오프 분석 (Trade-offs)

| 항목 | 선택된 결정 | 대안 | 선택 이유 (Gain) | 감수한 비용 및 완화책 (Pain & Mitigation) |
|---|---|---|---|---|
| **CI 테스트 계층** | **순수 도메인 JUnit5 + Testcontainers 스모크** | MockBukkit 단일 테스트 | 최신 패킷/엔티티 모킹 실패 방지 및 100% 신뢰성 있는 수학/상태 검증 | 실제 Paper 컨테이너 기동으로 CI 시간 약 20초 증가 -> 도메인 단위 테스트 우선 실행(Fail-fast) |
| **보안 스캔 도구** | **Aqua Security Trivy (Fail-fast 정책)** | 기본 빌드 배포 (취약점 점검 생략) | Base Image CVE 및 하드코딩 시크릿 자동 차단으로 DevSecOps 포트폴리오 차별화 | False Positive로 인한 빌드 지연 가능성 -> `.trivyignore`로 허용 가능한 취약점 명시적 관리 |
| **컨테이너 베이스** | **`eclipse-temurin:21-jre-jammy` (glibc)** | Alpine Linux (`musl libc`) | Netty 네이티브 epoll 가속 완벽 지원 (네트워크 처리량 최대 30% 향상) | Alpine 대비 이미지 크기 소폭 증가(약 100MB) -> 런타임 성능 및 안정성이 우선 |
| **플레이어 인증 포워딩** | **Velocity Modern Forwarding (HMAC-SHA256)** | BungeeCord 텍스트 IP 포워딩 | UUID 날조 및 백엔드 포트 스푸핑을 통한 관리자 권한 탈취 원천 차단 | 프록시-백엔드 간 비밀키 관리 부담 -> 환경변수 주입 및 Docker Secrets 격리 |
| **모니터링 스크랩** | **Prometheus Docker Service Discovery** | Static Configs 고정 IP 폴링 | 동적으로 생성/파기되는 아레나 컨테이너의 메트릭 누수 및 스크랩 에러 차단 | Docker 소켓 연동 설정 필요 -> 메트릭은 틱 종료 시 캐싱하여 메인 틱 블로킹 방지 |

