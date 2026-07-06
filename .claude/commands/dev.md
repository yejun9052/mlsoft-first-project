---
description: 백엔드(8080)·프론트엔드(5173) 개발 서버를 모두 실행하고 접속 주소를 안내
---

MLsoft 연차 관리 시스템의 백엔드와 프론트엔드 개발 서버를 실행하고, 준비가 끝나면 접속 주소를 알려줘.

프로젝트 루트: `C:\Users\User\Desktop\myproject\mlsoft-leave-system`

## 절차

### 0. 사전 확인 (PowerShell)

이미 떠 있는 서버는 다시 띄우지 말 것:

```powershell
Get-Service MySQL96 | Select-Object Status
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
```

- `MySQL96` 서비스가 Stopped면 `Start-Service MySQL96` 시도. 권한 오류가 나면 사용자에게 관리자 권한으로 시작해 달라고 안내하고 중단.
- 8080이 LISTEN이면 백엔드 기동 생략, 5173이 LISTEN이면 프론트 기동 생략. 둘 다 떠 있으면 바로 4번(주소 안내)으로.

### 1. 로그 디렉터리 준비

```powershell
New-Item -ItemType Directory -Force "C:\Users\User\Desktop\myproject\mlsoft-leave-system\.claude\logs"
```

### 2. 서버 기동 — 반드시 Start-Process로 디태치 실행

**주의: Bash/PowerShell 도구의 `run_in_background`로 서버를 띄우면 안 됨.** 세션 하네스가 나중에 백그라운드 작업을 정리하면서 서버도 함께 종료되는 문제가 두 번 재현됐다. 반드시 아래처럼 세션과 분리된 프로세스로 띄우고 출력은 로그 파일로 보낼 것.

백엔드 (8080이 안 떠 있을 때):

```powershell
Start-Process cmd.exe -WindowStyle Hidden -WorkingDirectory "C:\Users\User\Desktop\myproject\mlsoft-leave-system\backend" -ArgumentList '/c', 'set SPRING_PROFILES_ACTIVE=local&& "C:\Users\User\Desktop\myproject\mlsoft-leave-system\backend\gradlew.bat" bootRun > ..\.claude\logs\backend.log 2>&1'
```

(`gradlew.bat`은 반드시 절대 경로로 — 이 시스템은 cmd가 현재 디렉터리에서 배치 파일을 찾지 않아 상대 이름만 쓰면 실패한다.)

프론트엔드 (5173이 안 떠 있을 때):

```powershell
Start-Process cmd.exe -WindowStyle Hidden -WorkingDirectory "C:\Users\User\Desktop\myproject\mlsoft-leave-system\frontend" -ArgumentList '/c', 'npm run dev > ..\.claude\logs\frontend.log 2>&1'
```

### 3. 준비 대기

포트가 열릴 때까지 폴링한다. 프론트는 보통 15초 이내, 백엔드는 Gradle 빌드 포함 최대 120초까지 걸릴 수 있다:

```powershell
$deadline = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $deadline) {
  $b = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
  $f = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
  if ($b -and $f) { break }
  Start-Sleep -Seconds 5
}
if ($b) { "backend OK" } else { "backend NOT READY" }
if ($f) { "frontend OK" } else { "frontend NOT READY" }
```

시간 내에 안 뜨면 해당 로그(`\.claude\logs\backend.log` 또는 `frontend.log`)의 마지막 40줄을 읽어 원인을 파악하고 사용자에게 보고할 것. (백엔드 단골 원인: MySQL 미기동, 8080 점유, application-local.yml 누락)

### 4. 완료 보고

아래 형식으로 접속 주소를 안내:

- **접속 주소 (여기로 접속): http://localhost:5173**
- 백엔드 API: http://localhost:8080 (프론트가 `/api`, `/oauth2`, `/login/oauth2`를 프록시하므로 직접 열 일은 거의 없음)
- 로그: `.claude\logs\backend.log`, `.claude\logs\frontend.log`
- 종료 방법도 한 줄로 안내:
  ```powershell
  Get-NetTCPConnection -LocalPort 8080,5173 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
  ```
  (백엔드는 Gradle 데몬이 아니라 bootRun 프로세스만 죽음 — 정상)
