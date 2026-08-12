<#
.SYNOPSIS
  1차 테스트 감사 4종을 순차 실행한다 (기능·보안·예외·불변식).

.DESCRIPTION
  세션 하네스가 백그라운드 작업을 정리해도 죽지 않도록 Start-Process로 분리해 띄우는 것을
  전제로 만든 스크립트다 (CLAUDE.md "전체 개발 서버 기동" 항목과 같은 이유).

  각 단계의 산출물은 run-agent.ps1이 .codex/out/에 남기고, 이 스크립트는 진행 상황을
  상태 파일에 적는다. 전부 끝나면 마지막 줄에 ALL-DONE을 쓴다 — 그걸로 완료를 판정한다.
#>
$ErrorActionPreference = 'Continue'

$root   = 'C:\Users\User\Desktop\myproject\mlsoft-leave-system'
$runner = Join-Path $root '.codex\run-agent.ps1'
$status = Join-Path $root '.codex\out\_audit-status.txt'
$utf8   = New-Object System.Text.UTF8Encoding $false

# 순서: 보안 → 예외 → 불변식 → 적합성.
# 앞의 둘이 신규 이메일 코드를 보므로 먼저 돌린다 (가장 안 본 코드).
$steps = @(
    @{ Agent = 'security-auditor';         Task = '.codex\tasks\audit-1-security.md';    Label = '1/4 보안' },
    @{ Agent = 'failure-auditor';          Task = '.codex\tasks\audit-2-failure.md';     Label = '2/4 예외·경계값·동시성' },
    @{ Agent = 'leave-invariant-auditor';  Task = '.codex\tasks\audit-3-invariant.md';   Label = '3/4 연차 불변식' },
    @{ Agent = 'query-auditor';            Task = '.codex\tasks\audit-4-conformance.md'; Label = '4/4 요구사항 적합성·쿼리' }
)

function Write-Status([string]$line) {
    $stamp = (Get-Date).ToString('HH:mm:ss')
    Add-Content -Path $status -Value "[$stamp] $line" -Encoding UTF8
}

[System.IO.File]::WriteAllText($status, "1차 테스트 감사 시작: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`r`n", $utf8)

$before = @(Get-ChildItem (Join-Path $root '.codex\out') -Filter '*.md' | Select-Object -ExpandProperty Name)

foreach ($step in $steps) {
    Write-Status "$($step.Label) 시작 — $($step.Agent)"
    try {
        # 2>&1을 붙이면 안 된다 — PS 5.1은 네이티브 stderr(codex 버전 배너)를 ErrorRecord로 감싸고,
        # run-agent.ps1 내부가 $ErrorActionPreference='Stop'이라 그 자리에서 종료 예외가 된다.
        # 2026-08-12에 이걸로 4단계가 전부 2초 만에 "실패"했다.
        & $runner -Agent $step.Agent -Task $step.Task | Out-Null
        Write-Status "$($step.Label) 완료"
    }
    catch {
        Write-Status "$($step.Label) 실패 — $($_.Exception.Message)"
    }
}

# 이번 실행으로 새로 생긴 산출물만 추린다
$after = @(Get-ChildItem (Join-Path $root '.codex\out') -Filter '*.md' | Select-Object -ExpandProperty Name)
$new = $after | Where-Object { $before -notcontains $_ -and $_ -notlike '*.prompt.md' }

Write-Status "새 산출물 $($new.Count)건:"
foreach ($n in $new) { Write-Status "  $n" }
Write-Status 'ALL-DONE'
