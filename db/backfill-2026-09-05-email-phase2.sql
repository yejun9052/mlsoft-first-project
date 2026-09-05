-- ============================================================================
-- 이메일 2단계 W1 저장 구조 (2026-09-05)
--
-- 왜 바꾸는가:
--   · mail_credentials: 메일 발신 계정의 사용자명과 암호문을 DB에 보관하고,
--     다음 파도에서 환경변수를 fallback으로 사용할 수 있게 한다. 원문 비밀값은
--     이 파일과 DB에 넣지 않는다.
--   · email_templates: 관리자 편집 양식을 정책 key-value와 분리해 보관한다.
--   · leave_reminder_dispatch: 사원·주기·기간별 자동 발송 선점을 UNIQUE로 보장한다.
--   · email_history.status의 SENDING은 다중 인스턴스 발송 원자 선점용이다.
--   · admin_audit_log.action에는 이메일 일괄 발송·양식 수정·재발송 감사 행위를 추가한다.
--
-- 운영 프로필은 ddl-auto: validate이므로 기존 DB에는 이 파일을 먼저 적용해야 한다.
-- MySQL 8에는 ADD COLUMN IF NOT EXISTS가 없으므로 테이블은 information_schema
-- 존재 검사 후 prepared DDL로 만들고, ENUM은 전체 목록을 MODIFY COLUMN으로 맞춘다.
-- 모든 문장은 이미 반영된 DB에서 다시 실행해도 DO 0으로 건너뛰도록 작성했다.
-- ============================================================================

-- 메일 발신 계정 — provider별 한 행만 허용
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'mail_credentials') = 0,
               "CREATE TABLE `mail_credentials` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `active` bit(1) NOT NULL,
                  `encrypted_secret` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
                  `provider` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
                  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_mail_credentials_provider` (`provider`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 관리자 편집 이메일 양식 — 본문은 TEXT로 저장
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'email_templates') = 0,
               "CREATE TABLE `email_templates` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `created_at` datetime(6) NOT NULL,
                  `body_template` text COLLATE utf8mb4_unicode_ci NOT NULL,
                  `subject_template` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
                  `template_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
                  `updated_at` datetime(6) DEFAULT NULL,
                  `updated_by` bigint DEFAULT NULL,
                  `version` int NOT NULL DEFAULT '1',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_email_templates_template_key` (`template_key`),
                  KEY `FK_email_templates_updated_by` (`updated_by`),
                  CONSTRAINT `FK_email_templates_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 자동 발송 선점 이력 — 같은 사원·주기·기간은 한 번만 생성
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'leave_reminder_dispatch') = 0,
               "CREATE TABLE `leave_reminder_dispatch` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `created_at` datetime(6) NOT NULL,
                  `cycle` enum('D30','D60','D90','QUARTER') COLLATE utf8mb4_unicode_ci NOT NULL,
                  `email_history_id` bigint DEFAULT NULL,
                  `next_reset_date` date NOT NULL,
                  `period_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
                  `reference_date` date NOT NULL,
                  `remaining_days_snapshot` decimal(4,1) NOT NULL,
                  `result` enum('QUEUED','SKIPPED_NO_EMAIL') COLLATE utf8mb4_unicode_ci NOT NULL,
                  `sent_at` datetime(6) DEFAULT NULL,
                  `updated_at` datetime(6) DEFAULT NULL,
                  `user_id` bigint NOT NULL,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_leave_reminder_dispatch_user_cycle_period` (`user_id`,`cycle`,`period_key`),
                  KEY `FK_leave_reminder_dispatch_email_history` (`email_history_id`),
                  CONSTRAINT `FK_leave_reminder_dispatch_email_history` FOREIGN KEY (`email_history_id`) REFERENCES `email_history` (`id`),
                  CONSTRAINT `FK_leave_reminder_dispatch_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- email_history.status — SENDING 원자 선점 상태 추가
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'email_history'
                   AND COLUMN_NAME = 'status'
                   AND COLUMN_TYPE NOT LIKE '%SENDING%') > 0,
               "ALTER TABLE `email_history` MODIFY COLUMN `status` enum('FAILED','PENDING','SENDING','SENT') COLLATE utf8mb4_unicode_ci NOT NULL",
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- admin_audit_log.action — 이메일 관련 관리자 조작 감사 값 3개 추가
SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'admin_audit_log'
                   AND COLUMN_NAME = 'action'
                   AND (COLUMN_TYPE NOT LIKE '%EMAIL_BULK_SENT%'
                        OR COLUMN_TYPE NOT LIKE '%EMAIL_TEMPLATE_CHANGED%'
                        OR COLUMN_TYPE NOT LIKE '%EMAIL_RESENT%')) > 0,
               "ALTER TABLE `admin_audit_log` MODIFY COLUMN `action` enum('BASE_DAYS_CHANGED','CONFIG_CHANGED','DEPARTMENT_CHANGED','EMAIL_BULK_SENT','EMAIL_RESENT','EMAIL_TEMPLATE_CHANGED','ONBOARDING_APPROVED','ONBOARDING_REJECTED','ROLE_CHANGED','USER_RESTORED','USER_RETIRED') COLLATE utf8mb4_unicode_ci NOT NULL",
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 확인 쿼리 (실행 후 눈으로 볼 것) ────────────────────────────────────────
-- SHOW CREATE TABLE mail_credentials;
-- SHOW CREATE TABLE email_templates;
-- SHOW CREATE TABLE leave_reminder_dispatch;
-- SELECT COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME = 'email_history' AND COLUMN_NAME = 'status';
-- SELECT COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME = 'admin_audit_log' AND COLUMN_NAME = 'action';
