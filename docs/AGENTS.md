# 서브 에이전트 구성 가이드

> 다른 컴퓨터에서 이 프로젝트를 열었을 때 필요한 Claude Code 서브 에이전트 안내.
> **에이전트 정의는 `.claude/agents/`에 이미 포함되어 있으므로, 이 폴더를 git에 커밋하면 별도 세팅 없이 그대로 동작한다.**

## 구축된 프로젝트 에이전트 (`.claude/agents/`)

| 에이전트 | 파일 | 용도 | 언제 쓰나 |
|---|---|---|---|
| **spring-backend** | spring-backend.md | Spring Boot 백엔드 구현 | 엔티티/서비스/컨트롤러/스케줄러/Security 작업 |
| **react-frontend** | react-frontend.md | React + Tailwind 프론트 구현 | 페이지/컴포넌트/디자인 시스템/API 연동 |
| **db-designer** | db-designer.md | DB 스키마 설계·리뷰 | 테이블 변경, JPA 매핑 검증, 인덱스/FK 결정 |
| **style-reviewer** | style-reviewer.md | 코드 리뷰 (읽기 전용) | 기능 구현 완료 후 스타일·보안·컨벤션 점검 |

각 에이전트는 `docs/` 문서(스타일 가이드, DB 설계, 디자인 가이드)를 기준으로 동작하도록 작성되어 있다.
**설계 문서를 수정하면 에이전트 규칙도 자동으로 따라온다** (에이전트가 문서를 읽고 작업하기 때문).

## 기본 제공 에이전트 (Claude Code 내장 — 설치 불필요)

프로젝트 에이전트 외에 자주 쓸 내장 에이전트:
- `Explore` — 코드베이스 탐색 (참고자료/MLsoft 분석 등 읽기 전용 조사)
- `debugger` — 버그 원인 추적
- `test-verifier` — 구현 후 실제 동작 검증·테스트 작성
- `git-engineer` — 커밋/브랜치/PR 정리

## 다른 컴퓨터에서 시작하는 순서

1. 저장소 클론 (프라이빗 → `gh auth login` 필요)
2. Claude Code를 프로젝트 루트에서 실행 → `.claude/agents/`가 자동 인식됨
3. 시작 프롬프트 예시: "docs/ 폴더 읽고 현재 진행 상황 파악해줘"
4. 시크릿은 저장소에 없음 — `application-example.yml`을 복사해 로컬 설정 작성
   (DB 비밀번호, JWT secret, OAuth 클라이언트, data.go.kr API 키, 메일 계정)

## 에이전트 추가/수정 방법

`.claude/agents/이름.md` 파일 생성:
```markdown
---
name: 에이전트-이름
description: 언제 이 에이전트를 쓰는지 (Claude가 자동 선택 시 참고)
---
(시스템 프롬프트 — 역할, 참고 문서, 규칙)
```

## 앞으로 추가를 고려할 에이전트

- **email-engineer**: Java Mail Sender + 템플릿 + 발송 이력 구현이 본격화되면 분리
- **scheduler-tester**: 기산일/생일 스케줄러 시뮬레이션 검증 (이전 프로젝트에서 검증 못 한 부분)
