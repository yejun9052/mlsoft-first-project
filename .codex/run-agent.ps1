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

# Task가 파일 경로면 내용을 읽어 쓴다.
# -Encoding UTF8은 생략하면 안 된다 — PS 5.1의 Get-Content 기본값은 시스템 ANSI(여기선 cp949)라
# BOM 없는 UTF-8 한글이 그 자리에서 깨진다. 출력 인코딩만 고쳐 뒀던 탓에(2026-08-10)
# 역할 지시서가 모지바케로 Codex에 전달되고 있었다 (2026-08-11 발견).
$taskText = if (Test-Path $Task -PathType Leaf) { Get-Content $Task -Raw -Encoding UTF8 } else { $Task }

$outDir = Join-Path $PSScriptRoot 'out'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$stamp = Get-Date -Format 'MMdd-HHmmss'
$outFile = Join-Path $outDir "$Agent-$stamp.md"

$prompt = @"
$(Get-Content $agentFile -Raw -Encoding UTF8)

────────────────────────────────────────
# 이번 작업 지시

$taskText
"@

# 프롬프트도 UTF-8로 남긴다 — 나중에 "무엇을 물었는지" 확인할 때 읽혀야 한다
$promptFile = Join-Path $outDir "$Agent-$stamp.prompt.md"
$prompt | Out-File -FilePath $promptFile -Encoding utf8

$codexArgs = @('exec', '--sandbox', $Sandbox, '-C', $repoRoot, '--skip-git-repo-check')
if ($Model) { $codexArgs += @('-m', $Model) }

Write-Host "[$Agent] 실행 → $outFile"

# 인코딩 — 산출물이 읽을 수 없게 저장되던 것을 고쳤다 (2026-08-10).
# 두 가지가 겹쳐 있었다:
#   ① Windows PowerShell 5.1은 네이티브 프로세스 stdout을 콘솔 코드페이지(949)로 해석한다.
#      codex는 UTF-8로 내보내므로 한글이 그 자리에서 깨진다 → [Console]::OutputEncoding을 UTF-8로.
#   ② Tee-Object는 5.1에서 UTF-16LE로 쓰고 -Encoding 파라미터가 없다.
#      → 변수로 받아 화면에 출력하고, 파일은 Out-File -Encoding utf8로 따로 쓴다.
# 이걸 고치지 않으면 Claude Code가 .codex/out/*.md를 기계적으로 읽을 수 없다(AGENTS.md의 전제).
$previousOutputEncoding = [Console]::OutputEncoding
try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
    $result = Get-Content $promptFile -Raw -Encoding utf8 | & codex @codexArgs -
    $result | Write-Output
    $result | Out-File -FilePath $outFile -Encoding utf8
} finally {
    [Console]::OutputEncoding = $previousOutputEncoding
}

Write-Host "[$Agent] 완료 → $outFile"
