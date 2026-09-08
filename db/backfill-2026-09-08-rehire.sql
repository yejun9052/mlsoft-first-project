-- ============================================================================
-- 재입사자 처리 — 과거 근속 구간 테이블·감사 ENUM 보강 (2026-09-08)
--
-- 안 돌리면 무엇이 깨지는가:
--   1) 기존 운영 DB에 employment_periods가 없어 첫 재입사 처리에서 이전 근속을
--      저장할 수 없고, 운영 프로필(ddl-auto: validate)에서는 애플리케이션이 기동하지 않는다.
--   2) admin_audit_log.action에 USER_REHIRED가 없어 첫 재입사 감사 로그 INSERT가
--      MySQL에서 `Data truncated for column 'action'`으로 실패한다.
--   3) 기존 사원의 과거 근속은 소급 생성하지 않는다. 테이블만 만들고, 재입사 처리 시점부터
--      종료된 근속을 기록한다.
--
-- 멱등하다 — CREATE TABLE IF NOT EXISTS와 information_schema 조건부 MODIFY를 사용한다.
-- 실행 전 백업: mysqldump mlsoft_leave users admin_audit_log > backup-before-rehire.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS `employment_periods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `hire_date` date NOT NULL,
  `retired_at` date NOT NULL,
  `seq` int NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employment_periods_user_seq` (`user_id`,`seq`),
  CONSTRAINT `FK_employment_periods_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 action ENUM 전체를 유지한 채 USER_REHIRED를 추가한다.
SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'admin_audit_log'
        AND COLUMN_NAME = 'action'
        AND COLUMN_TYPE NOT LIKE '%USER_REHIRED%') > 0,
    "ALTER TABLE `admin_audit_log` MODIFY COLUMN `action` enum('BASE_DAYS_CHANGED','CONFIG_CHANGED','DEPARTMENT_CHANGED','EMAIL_BULK_SENT','EMAIL_RESENT','EMAIL_TEMPLATE_CHANGED','ONBOARDING_APPROVED','ONBOARDING_REJECTED','ROLE_CHANGED','USER_PURGED','USER_REHIRED','USER_RESTORED','USER_RETIRED') COLLATE utf8mb4_unicode_ci NOT NULL",
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 확인 쿼리 ──────────────────────────────────────────────────────────────
-- SELECT COUNT(*) AS '테이블 존재(1이어야 함)'
--   FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'employment_periods';
-- SELECT COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME = 'admin_audit_log' AND COLUMN_NAME = 'action';
-- USER_REHIRED가 목록에 있어야 한다.
