-- ============================================================================
-- 온보딩 승인 절차 도입 마이그레이션 + backfill (2026-08-08, 리뷰 S-1)
--
-- 입사일 자가 신고로 연차를 스스로 부여할 수 있던 구멍을 막으면서 `users.onboarding_status`가
-- 생겼다. 온보딩 완료 판별이 `hire_date IS NOT NULL`에서 이 컬럼으로 바뀌었다.
--
-- ⚠️ **2부를 빼먹으면 기존 사원 전원이 잠긴다.** 컬럼 기본값이 NOT_STARTED라
--    이미 근무 중인 사원까지 "온보딩 미완료"로 판정돼 /api/auth/* 밖이 전부 403이 되고,
--    스케줄러 3잡의 대상에서도 빠지며, 승인자 후보 목록도 비어 결재가 멈춘다.
--
-- 멱등하다 — 여러 번 실행해도 결과가 같다.
-- 실행 전 백업: mysqldump mlsoft_leave users > users-backup.sql
-- ============================================================================

-- ── 1부. 컬럼 추가 ──────────────────────────────────────────────────────────
-- 기본값을 NOT_STARTED로 두고 2부에서 기존 사원을 COMPLETED로 올린다.
-- 기본값 없이 NOT NULL로 추가하면 MySQL strict 모드에서 기존 행 때문에 ALTER가 실패한다.
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'users'
                   AND COLUMN_NAME = 'onboarding_status') = 0,
               'ALTER TABLE users ADD COLUMN onboarding_status ENUM(''COMPLETED'',''NOT_STARTED'',''PENDING_APPROVAL'') NOT NULL DEFAULT ''NOT_STARTED''',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 2부. 기존 사원을 완료로 표시 ────────────────────────────────────────────
--
-- 이 시점에 `hire_date`가 있는 사원은 전부 예전 온보딩을 통과한 사람이다
-- (승인 대기라는 상태 자체가 이번에 처음 생겼으므로 중간 상태가 존재할 수 없다).
--
-- 소급 검증은 하지 않는다 — 이미 부여된 연차를 지금 와서 회수하면 실제 사용분과 어긋난다.
-- 의심스러운 입사일이 있으면 아래 확인 쿼리로 뽑아 관리자가 개별 조정할 것
-- (PATCH /api/users/{id}/base-days는 감사 로그를 남긴다).
UPDATE users
SET onboarding_status = 'COMPLETED'
WHERE hire_date IS NOT NULL
  AND onboarding_status = 'NOT_STARTED';

-- ── 확인 쿼리 (실행 후 눈으로 볼 것) ────────────────────────────────────────
-- 1) 잠긴 사원이 없는지 — hire_date가 있는데 COMPLETED가 아니면 0행이어야 한다
-- SELECT id, name, email, hire_date, onboarding_status
--   FROM users WHERE hire_date IS NOT NULL AND onboarding_status <> 'COMPLETED';
--
-- 2) 소급 검토 대상 — 입사일이 가입 시점보다 한참 과거인 계정
-- SELECT id, name, email, hire_date, created_at, base_days,
--        DATEDIFF(DATE(created_at), hire_date) AS 신고_소급일수
--   FROM users
--  WHERE hire_date IS NOT NULL
--    AND hire_date < DATE_SUB(DATE(created_at), INTERVAL 90 DAY)
--  ORDER BY 신고_소급일수 DESC;
