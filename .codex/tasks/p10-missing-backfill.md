# 배포를 막고 있는 것: schema.sql ↔ backfill 전수 대조 + 누락 backfill 작성

## 배경

이 프로젝트는 스키마를 **두 경로**로 반영한다 (CLAUDE.md 배포 섹션의 규칙):

1. `db/schema.sql` — **빈 DB를 처음 만들 때만** 쓰인다 (compose가 mysql initdb에 마운트)
2. `db/backfill-<날짜>-<주제>.sql` — **이미 데이터가 있는 DB**는 이쪽으로만 변경을 받는다

운영은 `ddl-auto: validate`라 Hibernate가 테이블·컬럼을 만들지 않는다. 즉 **2번이 빠지면
운영 배포가 기동 단계에서 멈춘다.**

2026-08-11에 그 규칙이 지켜지지 않은 곳을 최소 3개 찾았다 — `schedule_entries`,
`schedule_dates`, `holidays`(개인 일정 08-07, 공휴일 API 08-08). 이 테이블을 만드는
backfill이 없다. **더 있는지 확인하고, 누락분을 만드는 것**이 이번 과제다.

## 1단계: 전수 대조 (이게 본론이다)

`db/schema.sql`이 정의하는 **16테이블의 모든 컬럼·인덱스·제약**을, 기존 backfill 6개가
만드는 것과 대조해라.

```
db/backfill-2026-08-07-O3.sql
db/backfill-2026-08-08-onboarding-status.sql
db/backfill-2026-08-08-query-indexes.sql
db/backfill-2026-08-08-scheduler.sql
db/backfill-2026-08-10-audit-log.sql
db/backfill-2026-08-10-welfare-fk-indexes.sql
```

**기준 시점**: "운영 DB는 2026-07-22 배포 상태"로 가정한다 — 그때가 마지막 배포 아티팩트
준비 시점이다(git log의 `4dbc8ce` 무렵). 그 이후 엔티티에 생긴 모든 변화가 대조 대상이다.
git으로 확인할 수 있으면 `git log --oneline -- db/` 와 엔티티 디렉터리 이력을 참고해라
(**읽기 전용 git 명령만** — AGENTS.md 절대 규칙 2).

표로 답해라:

| # | 테이블 | 대상 | schema.sql | backfill | 판정 |

`대상`은 `테이블 자체` / `컬럼 X` / `인덱스 X` / `FK X` / `UNIQUE X` 중 하나.
`판정`은 `양쪽 있음` / **`backfill 누락`** / `backfill만 있음(schema.sql 누락)` 셋 중 하나.

`backfill만 있음`도 반드시 보고해라 — 그건 반대 방향 사고다(빈 DB 첫 배포가 깨진다).

## 2단계: 누락분 backfill 작성

파일명: `db/backfill-2026-08-11-missing-tables.sql`

지켜야 할 것:

- **DDL은 `db/schema.sql`에서 그대로 떼 온다.** 손으로 쓰지 말 것 — 그 파일은 Hibernate가
  생성한 것을 덤프한 결과라 컬럼 타입·enum 값 목록·제약 이름이 정확하다. 손으로 쓰면
  `validate`가 거부하거나, 제약 이름이 갈려 빈 DB와 마이그레이션 DB가 달라진다.
- **멱등해야 한다.** 테이블은 `CREATE TABLE IF NOT EXISTS`로 충분하다.
  컬럼·인덱스·FK 추가가 필요하면 `information_schema`로 존재 여부를 보고 건너뛰는
  프로시저 방식을 쓴다 — 기존 파일들의 패턴을 그대로 따라라
  (`db/backfill-2026-08-10-welfare-fk-indexes.sql`이 가장 최신 예시다.
  그 파일은 FK 존재 여부를 **이름이 아니라 컬럼으로** 본다 — 이유가 주석에 있다).
- **생성 순서**: FK가 참조하는 테이블이 먼저다. `schedule_dates`는 `schedule_entries`를
  참조하므로 뒤에 온다.
- 파일 상단 주석에 **이 파일을 안 돌리면 무엇이 깨지는지** 한 줄로. 기존 파일들이 그렇게 돼 있다.
- 확인 쿼리를 파일 끝에 주석으로 (기존 파일 패턴).

## 3단계: 실행 순서

기존 backfill 6개 + 신규 1개를 **어떤 순서로** 운영 DB에 돌려야 하는지 한 줄씩.
의존 관계가 있으면 근거를 적어라 (예: 컬럼을 만드는 파일이 그 컬럼에 인덱스를 거는 파일보다 먼저).

## 출력 형식

`code-author` 역할의 형식대로. 단 1단계 표를 `## 결정`보다 **먼저** 놓아라 —
그 표가 이 과제의 산출물 중 가장 중요하다.

## 하지 말 것

- Flyway 도입을 제안하지 말 것. 별도 과제이고, **이 구멍을 먼저 메워야** 순서가 맞다
  (`docs/설계-초안/flyway-도입-초안-2026-08-11.md` 참고 — baseline이 잘못된 상태를 굳힌다)
- 엔티티나 애플리케이션 코드를 바꾸는 제안 금지. DB 쪽만이다
- `db/schema.sql`을 재생성하지 말 것 — 지금 파일이 정답이다
