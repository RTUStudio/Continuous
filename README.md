# Continuous

Velocity 프록시 서버용 대기열 및 재접속 관리 플러그인입니다. [LimboAPI](https://github.com/Elytrium/LimboAPI)를 사용하여 가상 대기 서버를 구현합니다.

## 주요 기능

### 🔄 서버 다운타임 시 자동 재접속 대기
메인 서버가 예기치 않게 종료되거나 재시작될 때, 플레이어는 자동으로 가상 대기 서버(Reconnect)로 이동합니다. 서버가 다시 온라인이 되면 자동으로 재접속됩니다.

### 📋 서버 만원 시 대기열 시스템
서버가 최대 플레이어 수에 도달하면 새로운 접속자는 가상 대기열 서버(Queue)로 이동합니다. 자리가 생기면 순서대로 서버에 입장합니다.

### ⚡ 우선순위 기반 접속
서버가 다시 온라인이 되면 다음 순서로 플레이어가 입장합니다:

1. **`continuous.admin` 권한 보유자** - `max-player` 구성을 무시하며 대기열을 건너뛰고 즉시 서버에 입장
1. **`continuous.bypass` 권한 보유자** - 대기열을 건너뛰고 즉시 서버에 입장
2. **Reconnect 대기자** - 원래 서버에 있다가 서버 다운으로 Reconnect로 이동한 플레이어
3. **`continuous.priority` 권한 보유자** - 각 대기열 내에서 우선순위를 받음
4. **기존 Queue 대기자** - 서버 다운 전부터 Queue에서 대기하던 플레이어  
5. **신규 Queue 대기자** - 서버가 켜진 후 새로 접속한 플레이어

## 동작 흐름

| 서버 상태 | 처리 흐름                                                                                                    |
|-----------|----------------------------------------------------------------------------------------------------------|
| **ONLINE · 만원** | - `continuous.admin` 권한자는 즉시 메인 서버 입장<br>- 신규 접속자는 Queue에서 대기                                            |
| **ONLINE · 여유 · 대기자 있음** | - `continuous.bypass` 권한자는 메인 서버 입장<br>- Queue 대기자는 순서대로 메인 서버로 이동<br>- 신규 접속자는 Queue가 비어있는 경우 메인 서버로 이동 |
| **ONLINE · 여유 · 대기자 없음** | - 신규 접속자는 바로 메인 서버 입장                                                                                    |
| **OFFLINE** | - 모든 접속자는 Reconnect 서버에서 대기                                                                              |

## 설치

### 요구사항
- Velocity 3.4.0 이상
- [LimboAPI](https://github.com/Elytrium/LimboAPI) 1.1.27 이상
- Java 21 이상

### 설치 방법
1. [LimboAPI](https://github.com/Elytrium/LimboAPI/releases)를 다운로드하여 `plugins` 폴더에 넣습니다.
2. Continuous JAR 파일을 `plugins` 폴더에 넣습니다.
3. Velocity 서버를 시작합니다.
4. `plugins/continuous/` 폴더에서 설정 파일을 수정합니다.

## 설정

### queue.yml
대기열 서버 설정 파일입니다.

```yaml
trigger: "((?i)^(server closed|server is restarting|multiplayer\\.disconnect\\.server_shutdown))+$"

server:
  check: 1000      # 서버 상태 확인 간격 (ms)
  timeout: 500     # 서버 핑 타임아웃 (ms)
  delay: 2000      # 서버 접속 지연 시간 (ms)

world:
  dimension: OVERWORLD
  gamemode: SPECTATOR
  light-level: 15
  location:
    x: 0
    y: 100
    z: 0
    yaw: 90.0
    pitch: 0.0
  schematic:
    load: false
    type: WORLDEDIT_SCHEM
    file: queue.schem
    offset:
      x: 0
      y: 64
      z: 0

full:
  message: "Server is full"
  title:
    title: ""
    subtitle: "<red>Server is full</red>"

queue:
  message: "Queue: {0}"
  title:
    title: ""
    subtitle: "Queue: {0}"
  max-player:
    enabled: false
    size: 100

connect:
  message: "Connecting!"
  title:
    title: ""
    subtitle: "<green>Connecting...</green>"
```

### reconnect.yml
재접속 대기 서버 설정 파일입니다.

```yaml
trigger: "((?i)^(server closed|server is restarting|multiplayer\\.disconnect\\.server_shutdown))*$"

server:
  check: 1000
  timeout: 500
  delay: 2000

world:
  dimension: OVERWORLD
  gamemode: SPECTATOR
  light-level: 15
  location:
    x: 0
    y: 100
    z: 0
    yaw: 90.0
    pitch: 0.0
  schematic:
    load: false
    type: WORLDEDIT_SCHEM
    file: reconnect.schem
    offset:
      x: 0
      y: 64
      z: 0

offline:
  message: "Server is restarting!"
  title:
    title: ""
    subtitle: "<gold>Server is restarting...</gold>"

connect:
  message: "Connecting!"
  title:
    title: ""
    subtitle: "<green>Connecting...</green>"
```

## 권한

| 권한 | 설명 |
|------|------|
| `continuous.admin` | `max-player` 구성을 무시하며 대기열을 건너뛰고 즉시 서버에 입장합니다 |
| `continuous.bypass` | 서버에 여유가 있을 때 대기열을 건너뛰고 즉시 서버에 입장합니다 |
| `continuous.priority` | 각 대기열 내에서 우선순위를 받습니다 |
| `continuous.reload` | `/continuous reload` 명령어를 사용할 수 있습니다 |

## 명령어

| 명령어 | 설명 |
|--------|------|
| `/continuous reload` | 설정 파일을 다시 불러옵니다 |

## Schematic 지원

WorldEdit `.schem` 파일을 사용하여 대기 서버의 월드를 꾸밀 수 있습니다.

1. `plugins/Continuous/Schematics/` 폴더에 `.schem` 파일을 넣습니다.
2. 설정 파일에서 `schematic.load: true`로 변경합니다.
3. `schematic.file`에 파일 이름을 지정합니다.

## 빌드

```bash
./gradlew build
```

빌드된 JAR 파일은 `build/libs/` 폴더에 생성됩니다.

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 크레딧

- [LimboAPI](https://github.com/Elytrium/LimboAPI) - Elytrium
- 개발: IPECTER
