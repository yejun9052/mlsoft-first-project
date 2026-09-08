-- ============================================================================
-- 퇴직자 데이터 파기 P1 스키마 보강 (2026-09-08)
--
-- 안 돌리면 무엇이 깨지는가:
--   1) 기존 운영 DB에는 users.purged_at / users.purge_hold_reason이 없어
--      P2 파기 기능이 해당 컬럼을 읽거나 기록할 때 실패한다.
--   2) admin_audit_log.action에 USER_PURGED가 없어 첫 파기 감사 로그 INSERT가
--      MySQL에서 `Data truncated for column 'action'`으로 실패한다.
--   3) ddl-auto: validate는 MySQL ENUM의 값 목록까지 검사하지 않으므로,
--      애플리케이션 기동만으로는 2번 누락을 발견하지 못한다.
--
-- 기본 파기 모드는 MANUAL이며 P1에서는 파기 로직을 실행하지 않는다.
-- 운영 프로필은 ddl-auto: validate이므로 schema.sql은 신규 DB용, 이 파일은
-- 이미 데이터가 있는 DB용이다. 실행 전 백업: mysqldump mlsoft_leave users admin_audit_log > backup.sql
--
-- 멱등하다 — MySQL 8의 ADD COLUMN IF NOT EXISTS를 사용하지 않고
-- information_schema로 존재 여부를 확인한다.
-- ============================================================================

SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'users'
        AND COLUMN_NAME = 'purged_at') = 0,
    'ALTER TABLE `users` ADD COLUMN `purged_at` DATETIME(6) NULL AFTER `retired_at`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'users'
        AND COLUMN_NAME = 'purge_hold_reason') = 0,
    'ALTER TABLE `users` ADD COLUMN `purge_hold_reason` VARCHAR(255) NULL AFTER `purged_at`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 기존 action ENUM 전체를 유지한 채 USER_PURGED를 추가한다.
SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'admin_audit_log'
        AND COLUMN_NAME = 'action'
        AND COLUMN_TYPE NOT LIKE '%USER_PURGED%') > 0,
    "ALTER TABLE `admin_audit_log` MODIFY COLUMN `action` enum('BASE_DAYS_CHANGED','CONFIG_CHANGED','DEPARTMENT_CHANGED','EMAIL_BULK_SENT','EMAIL_RESENT','EMAIL_TEMPLATE_CHANGED','ONBOARDING_APPROVED','ONBOARDING_REJECTED','ROLE_CHANGED','USER_PURGED','USER_RESTORED','USER_RETIRED') COLLATE utf8mb4_unicode_ci NOT NULL",
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 파기 대상 자유 텍스트는 익명화 시 NULL이 된다. 기존 운영 DB의 NOT NULL을 함께 완화한다.
SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'leave_requests'
        AND COLUMN_NAME = 'request_reason'
        AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE `leave_requests` MODIFY COLUMN `request_reason` varchar(255) COLLATE utf8mb4_unicode_ci NULL',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'welfare_requests'
        AND COLUMN_NAME = 'reason'
        AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE `welfare_requests` MODIFY COLUMN `reason` varchar(255) COLLATE utf8mb4_unicode_ci NULL',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'leave_action_history'
        AND COLUMN_NAME = 'comment'
        AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE `leave_action_history` MODIFY COLUMN `comment` varchar(255) COLLATE utf8mb4_unicode_ci NULL',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'welfare_action_history'
        AND COLUMN_NAME = 'comment'
        AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE `welfare_action_history` MODIFY COLUMN `comment` varchar(255) COLLATE utf8mb4_unicode_ci NULL',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'email_history'
        AND COLUMN_NAME = 'title'
        AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE `email_history` MODIFY COLUMN `title` varchar(255) COLLATE utf8mb4_unicode_ci NULL',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'email_history'
        AND COLUMN_NAME = 'content'
        AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE `email_history` MODIFY COLUMN `content` text COLLATE utf8mb4_unicode_ci NULL',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 확인 쿼리 (실행 후 눈으로 확인할 것)
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND ((TABLE_NAME = 'users' AND COLUMN_NAME IN ('purged_at', 'purge_hold_reason'))
--      OR (TABLE_NAME = 'admin_audit_log' AND COLUMN_NAME = 'action'));
-- admin_audit_log.action COLUMN_TYPE에 USER_PURGED가 있어야 한다.
