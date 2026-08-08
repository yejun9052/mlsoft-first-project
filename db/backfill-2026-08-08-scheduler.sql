-- ============================================================================
-- 스케줄러 도입 마이그레이션 + backfill (2026-08-08, docs/09 §3)
--
-- 운영 프로필은 ddl-auto: validate 다. Hibernate가 컬럼을 만들어 주지 않으므로
-- **이 스크립트를 배포 전에 먼저 실행해야 한다** — 안 하면 애플리케이션이 기동 단계에서
-- "missing column" 으로 멈춘다. db/schema.sql은 빈 DB를 처음 만들 때만 쓰이고
-- 이미 데이터가 있는 DB에는 적용되지 않는다.
--
--   1부: 컬럼 3개 추가 (DDL)
--   2부: 기존 행 보정 (DML)
--
-- 2부를 그냥 두면 두 가지가 잘못된다:
--   - 온보딩 때 이미 소급 적립한 월차를 스케줄러가 한 번 더 준다 (monthly_granted_count = 0)
--   - 올해 생일이 이미 지난 사원에게 반차가 지금 소급 지급된다 (last_birthday_grant_year = NULL)
--
-- 멱등하다 — 여러 번 실행해도 결과가 같다. DDL도 information_schema로 존재 여부를 보고 건너뛴다
-- (MySQL 8에는 ADD COLUMN IF NOT EXISTS 가 없다).
-- 실행 전 백업: mysqldump mlsoft_leave users leave_reset_history > backup.sql
-- ============================================================================

-- ── 1부. 컬럼 추가 ──────────────────────────────────────────────────────────

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'users'
                   AND COLUMN_NAME = 'monthly_granted_count') = 0,
               'ALTER TABLE users ADD COLUMN monthly_granted_count INT NOT NULL DEFAULT 0',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'users'
                   AND COLUMN_NAME = 'last_birthday_grant_year') = 0,
               'ALTER TABLE users ADD COLUMN last_birthday_grant_year INT NULL',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'leave_reset_history'
                   AND COLUMN_NAME = 'carried_bonus_days') = 0,
               'ALTER TABLE leave_reset_history ADD COLUMN carried_bonus_days DECIMAL(4,1) NOT NULL DEFAULT 0.0',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 2부-1. 월차 누적 적립 횟수 ──────────────────────────────────────────────
--
-- 근거: AuthService.completeOnboarding은 1년 미만 신입에게
--       base_days = MIN(입사 후 경과 만개월, 상한) 을 통째로 부여한다. 그 시점 이후로
--       월차를 적립한 경로가 없었으므로(스케줄러 0건) base_days가 곧 적립 횟수다.
--
-- 한계: 관리자가 PATCH /api/users/{id}/base-days로 연차를 직접 고쳤다면 이 값이 오염된다.
--       그래서 상한(11)으로 자르고, 1년 이상 근속자는 건드리지 않는다(월차 대상이 아님).
--       해당자가 있으면 아래 확인 쿼리로 먼저 눈으로 볼 것.
UPDATE users
SET monthly_granted_count = LEAST(GREATEST(FLOOR(base_days), 0), 11)
WHERE hire_date IS NOT NULL
  AND hire_date > DATE_SUB(CURDATE(), INTERVAL 1 YEAR)
  AND monthly_granted_count = 0;

-- ── 2부-2. 생일 반차 지급 연도 ──────────────────────────────────────────────
--
-- 올해 생일이 이미 지난 사원은 "올해분을 받은 것으로" 표시한다.
-- 지금까지 생일 반차 지급 기능 자체가 없었으므로 실제로는 못 받았지만,
-- 스케줄러 도입 첫날에 전 사원에게 소급 지급되는 편이 더 큰 사고다.
-- 소급이 필요하면 관리자가 복리후생(보너스 연차)으로 개별 처리하는 것이 감사 흔적도 남는다.
--
-- 2/29 생일은 평년에 2/28로 본다 — BirthdayLeaveGrantService의 LocalDate.withYear 보정과
-- 같은 기준이어야 한다. 문자열 그대로 비교하면 평년 2/28에 "02-29" <= "02-28"이 거짓이라
-- 표시가 누락되고, 그날 00:10 스케줄러가 그 사원에게 소급 지급한다.
UPDATE users
SET last_birthday_grant_year = YEAR(CURDATE())
WHERE hire_date IS NOT NULL
  AND birth_day IS NOT NULL
  AND last_birthday_grant_year IS NULL
  AND (CASE
           WHEN DATE_FORMAT(birth_day, '%m-%d') = '02-29'
                AND DAY(LAST_DAY(CONCAT(YEAR(CURDATE()), '-02-01'))) = 28
               THEN '02-28'
           ELSE DATE_FORMAT(birth_day, '%m-%d')
       END) <= DATE_FORMAT(CURDATE(), '%m-%d');

-- ── 확인 쿼리 (실행 후 눈으로 볼 것) ────────────────────────────────────────
-- SELECT id, name, hire_date, base_days, monthly_granted_count
--   FROM users
--  WHERE hire_date > DATE_SUB(CURDATE(), INTERVAL 1 YEAR)
--  ORDER BY hire_date;
--
-- SELECT id, name, birth_day, last_birthday_grant_year FROM users WHERE birth_day IS NOT NULL;
