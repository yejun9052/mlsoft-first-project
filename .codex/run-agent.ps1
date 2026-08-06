<#
.SYNOPSIS
  Codex 서브 에이전트 실행기 — 역할 지시서(.codex/agents/*.md) + 작업 지시를 합쳐 codex exec에 넘긴다.

.DESCRIPTION
  Codex CLI에는 서브 에이전트 개념이 없다. 대신 "역할 지시서 + 1회성 작업 지시"를 조합해
  같은 효과를 낸다. 역할 지시서는 재사용되고(산출물 형식이 고정되므로 기계적으로 읽을 수 있다),
  작업 지시만 매번 바뀐다.

  기본 샌드박스는 read-only다 — 이 머신(Windows)에서 workspace-write가 동작하지 않는 것이
  확인됐고, 쓰기는 Claude Code 쪽이 담당한다. 그래서 역할 지시서들이 "고치지 말고 적용 가능한
  전체 코드를 출력하라"고 요구한다.

.PARAMETER Agent
  .codex/agents/ 안의 역할 이름 (확장자 없이). 예: leave-invariant-auditor

.PARAMETER Task
  이번 작업 지시. 파일 경로를 주면 그 파일 내용을 지시로 쓴다.

.PARAMETER Model
  모델 지정 (기본: codex 설정값)

.EXAMPLE
  .\.codex\run-agent.ps1 -Agent leave-invariant-auditor -Task "User.java의 잔액 필드 대입 전부 감사해라"

.EXAMPLE
  .\.codex\run-agent.ps1 -Agent test-author -Task .codex\tasks\i1-tests.md
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Agent,
    [Parameter(Mandatory = $true)][string]$Task,
    [string]$Model,
    [ValidateSet('read-only', 'workspace-write')][string]$Sandbox = 'read-only'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$agentFile = Join-Path $PSScriptRoot "agents\$Agent.md"

if (-not (Test-Path $agentFile)) {
    $available = (Get-ChildItem (Join-Path $PSScriptRoot 'agents') -Filter '*.md' |
        ForEach-Object { $_.BaseName }) -join ', '
    throw "역할 지시서를 찾을 수 없다: $agentFile`n사용 가능한 역할: $available"
}

# Task가 파일 경로면 내용을 읽어 쓴다
$taskText = if (Test-Path $Task -PathType Leaf) { Get-Content $Task -Raw } else { $Task }

$outDir = Join-Path $PSScriptRoot 'out'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$stamp = Get-Date -Format 'MMdd-HHmmss'
$outFile = Join-Path $outDir "$Agent-$stamp.md"

$prompt = @"
$(Get-Content $agentFile -Raw)

────────────────────────────────────────
# 이번 작업 지시

$taskText
"@

$promptFile = Join-Path $outDir "$Agent-$stamp.prompt.md"
Set-Content -Path $promptFile -Value $prompt -Encoding utf8

$codexArgs = @('exec', '--sandbox', $Sandbox, '-C', $repoRoot, '--skip-git-repo-check')
if ($Model) { $codexArgs += @('-m', $Model) }

Write-Host "[$Agent] 실행 → $outFile"
Get-Content $promptFile -Raw | & codex @codexArgs - | Tee-Object -FilePath $outFile
Write-Host "[$Agent] 완료 → $outFile"
