# Flyway 도입 설계 + 마이그레이션 파일 작성

docs/12 ⑧ / docs/08 Y-6. 지금은 마이그레이션 도구가 없다:
- 로컬·개발: `ddl-auto: update`
- 운영: `ddl-auto: validate` + `db/schema.sql`(최초 1회) + `db/backfill-*.sql`(수동 5개)

이 방식의 문제는 **적용 여부를 아무도 추적하지 않는다는 것**이다. backfill을 돌렸는지는
사람의 기억에 있고, 실제로 "운영 DB에 아직 안 돌림"이 5개 쌓여 있다.

## 지금 있는 것 (전부 읽어라)

```
db/schema.sql                                   -- 16테이블, 최초 스키마
db/backfill-2026-08-07-O3.sql                   -- 부서 active·advance_days 보정
db/backfill-2026-08-08-onboarding-status.sql    -- 컬럼 추가 + 2부 값 보정
db/backfill-2026-08-08-query-indexes.sql        -- 인덱스 11
db/backfill-2026-08-08-scheduler.sql            -- 컬럼 3 + 값 보정
db/backfill-2026-08-10-audit-log.sql            -- admin_audit_log 신설
db/backfill-2026-08-10-welfare-fk-indexes.sql   -- 복리후생 FK 2 + 인덱스 3
```

`docker-compose.prod.yml`이 `schema.sql`을 mysql 컨테이너의
`/docker-entrypoint-initdb.d/`에 마운트한다. `application-prod.yml`은 `validate`다.

## 답해야 할 것 (순서대로)

### 1. baseline 전략 — 셋 중 하나를 고르고 근거를 써라

이 프로젝트에는 **상태가 서로 다른 DB 3종**이 이미 존재한다:
- (a) 로컬 개발 DB — `ddl-auto: update`로 최신 스키마 + backfill 일부 적용
- (b) 홈서버 운영 DB — 구 스키마, **backfill 5개 미적용**
- (c) 빈 DB — 신규 배포

`baselineOnMigrate`를 쓸지, V1을 schema.sql로 둘지, 아니면 (b)를 먼저 손으로 맞춘 뒤
baseline을 걸지. **(b)를 어떻게 처리하는지가 이 결정의 핵심이다** — 여기서 틀리면
운영 DB에 이미 있는 테이블을 다시 만들려 하거나, 반대로 미적용 backfill이 영구히 건너뛰어진다.

### 2. 마이그레이션 파일 목록과 내용

`backend/src/main/resources/db/migration/V__*.sql` 로 만들 파일 전체를 출력해라.
기존 backfill의 **멱등 프로시저 방식을 그대로 옮기지 말 것** — Flyway가 적용 여부를
추적하므로 `information_schema` 검사가 불필요해지고, 그게 도입 이유의 절반이다.
단, baseline 전략상 "이미 적용된 DB에서도 안전해야" 하는 파일이 있으면 그건 남겨라.

### 3. 설정 변경

- `build.gradle` 의존성 (Flyway + MySQL 모듈. Spring Boot 4 / Java 21 기준 버전 확인)
- `application.yml` / `-local.yml` / `-prod.yml` 의 `flyway.*`와 `ddl-auto` 변경안.
  **운영은 validate를 유지하는가, Flyway에 맡기고 none으로 내리는가** — 판단과 근거
- `docker-compose.prod.yml`의 `schema.sql` 마운트를 **제거해야 하는가** (Flyway와 이중 적용 위험)
- 테스트는 H2 인메모리다. **Flyway를 테스트 프로필에서 끌 것인가**(현재 `create-drop`/`update` 동작
  확인) — 켜면 MySQL 문법 마이그레이션이 H2에서 깨진다. 이건 반드시 답해라

### 4. 되돌릴 수 없는 위험

Flyway 도입 후 **처음 배포할 때** 무엇이 잘못될 수 있는지, 각각을 배포 전에 어떻게 확인하는지.
`checksum mismatch`로 기동이 막히는 경우를 포함해라.

## 출력 형식

`AGENTS.md` 절대 규칙 4 — 파일 경로 + 완성된 전체 내용. 설정 파일은 **변경 후 전문**을 내라
(diff나 "이 줄 추가" 형태 금지 — 적용하는 쪽이 판단할 여지를 남기면 안 된다).

마지막에 한 줄로: `권장: 지금 도입 / 배포 후 도입 / 도입하지 말 것` + 근거 한 문장.
