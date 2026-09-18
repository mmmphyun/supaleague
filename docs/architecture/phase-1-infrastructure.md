# Phase 1: 인프라 및 인스턴스 라이프사이클 아키텍처 명세서

## 1. 개요 및 기술 스택
- **프로젝트 명**: SupaLeague (마인크래프트 No-Mod 축구 플랫폼)
- **개발 언어**: Kotlin (JVM 타겟)
- **프로젝트 구조**: Gradle 멀티 모듈
  - `:common` (도메인 DTO, 패킷 규격, 공유 상수)
  - `:velocity-proxy` (Velocity 3.x 프록시 플러그인)
  - `:lobby-plugin` (Paper 로비 및 매치 대기실 플러그인)
  - `:arena-plugin` (Paper 경기장 인스턴스 플러그인, 틱 최적화)
  - `:orchestrator` (Ktor + Coroutines 기반 Docker 컨테이너 오케스트레이터)
- **인프라 런타임**:
  - Docker Engine Daemon
  - Redis 7.x (Pub/Sub 메시지 브로커 및 인스턴스 상태 저장소)
  - Velocity Proxy Node

---

## 2. 네트워크 토폴로지 및 라우팅 전략

```
                 [Internet]
                     │ (TCP 25565)
            [HAProxy / Envoy L4]
                     │ (PROXY Protocol v2)
           [Velocity Proxy Node]
                     │ (Internal Docker Bridge: 'supa-net')
       ┌─────────────┴─────────────────────────────────┐
       ▼                                               ▼
[Lobby Server (Paper)]                      [Arena Server (Paper)]
  (172.20.0.10:25565)                         (172.20.0.X:25565)
```

- **Docker 커스텀 브리지 (`supa-net`) 격리**:
  - 백엔드 컨테이너(로비, 아레나)는 호스트 포트 바인딩(포트 포워딩) 배제.
  - Linux 커널의 `iptables` NAT 테이블 오버헤드 및 호스트 포트 고갈(Port Exhaustion) 차단.
  - Velocity는 컨테이너 내부 가상 IP(`172.20.0.X:25565`)로 직결.
- **동적 서버 등록 파이프라인**:
  1. Orchestrator가 새 아레나 컨테이너를 생성하고 TCP 25565 포트 오픈 헬스체크.
  2. Orchestrator가 Redis Pub/Sub(`velocity:server:register`) 메시지 발행.
  3. Velocity 플러그인이 `ProxyServer.registerServer(ServerInfo)` API를 호출하여 무중단 라우팅 테이블 갱신.
  4. 경기 종료 시 `velocity:server:unregister`를 통해 라우팅 테이블 정리.

---

## 3. 인스턴스 수명주기 및 상태 머신 (Lifecycle State Machine)

```
 [START]
    │
    ▼
[PROVISIONING] ──(JVM 부팅 & 헬스체크 통과)──> [WARM_STANDBY] (대기 풀)
                                                    │
                                                    │ 양 팀 '준비 완료' 확정 (Lazy Allocation)
                                                    ▼
[TERMINATING] <──(경기 종료 / 타임아웃)────── [ASSIGNED / IN_GAME]
    │
    ├─(재사용 가능 판정)──> 월드 초기화(Soft-Reset) ──> [WARM_STANDBY] 복귀
    │
    ▼ (완전 파기)
 [DEAD] (docker rm -f & Velocity 등록 해제)
```

### 3.1 상태 정의
- **PROVISIONING**: Docker 컨테이너 생성 및 JVM 부팅 단계.
- **WARM_STANDBY**: 웜 풀(Warm Pool)에서 유휴 상태로 대기. 즉시 매치 투입 가능.
- **ASSIGNED**: 매치가 매핑되고 플레이어가 핸드오프 중인 상태.
- **IN_GAME**: 경기 진행 중. 매 5초마다 Orchestrator로 틱/상태 하트비트 전송.
- **TERMINATING**: 경기 종료 후 플레이어를 로비로 리다이렉트(Draining).
- **DEAD**: 컨테이너 강제 파기 및 리소스 회수.

---

## 4. 워크로드 및 용량 산정 (Bin-Packing Policy)

### 4.1 컨테이너 규격
| 타입 | 용도 | 인원 구성 | CPU/Memory 제한 | Paper 힙 설정 |
|---|---|---|---|---|
| **Type-A** | 11v11 정규 구장 | 선수 22명 (관중 차단/극소수) | 1.0 Core / 1024MB | `-Xms512M -Xmx768M` |
| **Type-B** | 1v1~5v5 풋살 구장 | 2~4개 풋살장 (최대 24명) | 1.0 Core / 768MB | `-Xms384M -Xmx512M` |

### 4.2 아레나 인스턴스 경량화 설정
- `server.properties`: `view-distance=3`, `simulation-distance=3`, `allow-nether=false`, `generate-structures=false`
- `paper-world-defaults.yml`: 스폰 청크 크기 0, 몬스터/동물 스폰 완전 비활성화, 잔디 번식 및 날씨 연산 비활성화.
- 불필요한 바닐라 틱 루프 제거로 512MB 힙 내에서 안정적 20 TPS 보장.

---

## 5. 급격한 컨테이너 회전(Churn) 방지 정책

1. **지연 바인딩 (Lazy Container Provisioning)**:
   - 로비 서버 내 'Ready Room'에서 팀 구성, 선수 교체, 인게임 세팅을 확정.
   - 양 팀 전원이 '준비 완료'를 누른 순간에만 실제 아레나 컨테이너를 할당하여 취소 시의 I/O 낭비 방지.
2. **토큰 버킷 기반 매치 쿨다운 (Rate Limiting)**:
   - Redis 토큰 버킷(`match:cooldown:{team_id}`)을 통해 매치 생성 시 최소 30초 쿨다운 강제.
   - 5분 이내 연속 매치 취소 3회 발생 시 3분간 매치 생성 차단.
3. **소프트 리셋 재사용 (Warm Recycling)**:
   - 단기 취소 및 정상 종료 시 컨테이너를 파기하지 않고 인게임 엔티티 및 점수판만 초기화하여 `WARM_STANDBY`로 환원.

---

## 6. 관중 중계 아키텍처 (릴레이 분리)
- **아레나 직접 관전 배제**:
  - 관중 수가 늘어날 경우 아레나 서버의 Netty 패킷 브로드캐스팅($O(N)$) 부하로 인해 선수들의 틱 딜레이가 발생하는 현상 차단.
- **Relay 패턴 (GOTV/HLTV 구조)**:
  - 아레나 서버는 단 1개의 릴레이 세션에만 경기 상태 패킷을 송신.
  - 관전자는 별도의 '중계 전용 Paper 서버'에 접속하며, 하프라인 상공의 더미 엔티티 시점 고정(`ClientboundSetCameraPacket`)을 통해 공을 추적하는 TV 중계 뷰 시청.

---

## 7. 통신 프로토콜 규격 (Redis Pub/Sub & Key-Value)

### 7.1 Redis 토픽
- `match:request`: 로비 -> 오케스트레이터 (매치 생성 요청)
- `velocity:server:register`: 오케스트레이터 -> Velocity (서버 라우팅 테이블 등록)
- `velocity:server:unregister`: 오케스트레이터 -> Velocity (서버 라우팅 테이블 제거)
- `velocity:player:send`: 오케스트레이터 -> Velocity (플레이어 서버 이동 지시)
- `arena:heartbeat`: 아레나 -> 오케스트레이터 (틱 상태 및 하트비트 전송)

### 7.2 고아 컨테이너 방어 정책
- 아레나 서버 플러그인은 60초 이상 Redis 하트비트 응답이 없거나 오케스트레이터와 연결 단절 시 자체적으로 `Bukkit.shutdown()` 호출(Self-Termination).
- 오케스트레이터 재부팅 시 `docker ps --filter label=project=supaleague` 필터로 잔여 컨테이너를 스캔하여 상태 불일치 해소(Reconciliation Loop).

---

## 8. 핵심 설계 트레이드오프 분석 (Trade-offs)

| 항목 | 선택된 결정 | 대안 | 선택 이유 (Gain) | 감수한 비용 및 완화책 (Pain & Mitigation) |
|---|---|---|---|---|
| **인스턴스 격리** | **Docker 컨테이너 분리** | 단일 JVM 멀티월드 (SlimeWorldManager) | 경기장 간 하드웨어 자원(cgroups) 및 결함 완전 격리, DevSecOps 역량 입증 | 컨테이너당 JVM 풋프린트 오버헤드 발생 -> 네더/엔드 제거, 뷰거리 3 축소로 512MB 다이어트 |
| **오케스트레이터 언어** | **Kotlin + Coroutines** | Go (Moby SDK) | Velocity, Paper, DTO 도메인 모델 100% 코드 공유, 언어 컨텍스트 스위칭 제로 | JVM 상주 메모리(100MB 수준) 증가 -> 단일 책임 경량 프로세스로 최적화 |
| **프로비저닝 시점** | **웜 풀(Warm Pool) + 지연 바인딩** | 온디맨드 즉시 생성 (`docker run`) | JVM 부팅 15~20초 콜드 스타트 제거, 준비 완료 즉시 매치 투입 | 유휴 컨테이너 1~2개 상시 메모리 점유 -> 소프트 리셋 재사용으로 순환 주기 최소화 |
| **네트워크 바인딩** | **내부 Docker Bridge (`supa-net`) 직결** | 호스트 포트 매핑 (`-p 30000+:25565`) | 호스트 포트 고갈 방지 및 Linux iptables NAT 오버헤드 완전 제거 | 외부 직접 접속 불가 (반드시 Velocity 프록시를 통해서만 진입 가능, 보안상 오히려 이점) |
| **관중 트래픽 처리** | **릴레이(Relay/GOTV) 서버 분리** | 아레나 서버 직접 관전 | 아레나 서버의 관중 패킷 브로드캐스트 부하 $O(1)$ 격리 | 릴레이 전용 인스턴스 1대 추가 리소스 소모 -> 관중이 없을 때는 릴레이 컨테이너 미기동 |

