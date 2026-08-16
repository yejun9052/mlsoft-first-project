-- ============================================================================
-- 승인 대기 중 온보딩 1회 수정 컬럼 추가 (2026-08-16)
--
-- 운영 프로필은 ddl-auto: validate 다. Hibernate가 컬럼을 만들어 주지 않으므로
-- **이 스크립트를 애플리케이션 배포 전에 먼저 실행해야 한다** — 실행하지 않으면 기동 단계에서
-- "missing column" 오류로 멈춘다. db/schema.sql은 빈 DB를 처음 만들 때만 사용된다.
--
-- 기존 사용자는 기능 도입 전에 수정권을 사용한 적이 없으므로 false로 보정한다.
-- 멱등하다 — information_schema에서 컬럼 존재 여부를 확인해 여러 번 실행해도 안전하다.
-- MySQL 8에는 ADD COLUMN IF NOT EXISTS가 없으므로 prepared statement를 사용한다.
--
-- leave_policy_config 행은 넣지 않는다. PolicyConfigKey를 기준으로 DataInitializer가 시딩하며,
-- 시딩 전 행이 없더라도 PolicyConfigReader가 카탈로그 기본값 true를 사용한다.
--
-- 실행 전 백업: mysqldump mlsoft_leave users > backup-users.sql
-- ============================================================================

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'users'
                   AND COLUMN_NAME = 'onboarding_revised') = 0,
               'ALTER TABLE users ADD COLUMN onboarding_revised BIT(1) NOT NULL DEFAULT b''0'' AFTER name',
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 확인 쿼리 (실행 후 눈으로 볼 것) ────────────────────────────────────────
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME = 'users'
--    AND COLUMN_NAME = 'onboarding_revised';
--
-- SELECT onboarding_revised, COUNT(*)
--   FROM users
--  GROUP BY onboarding_revised;
