-- ============================================================================
-- 이메일 건별 알림 인프라 마이그레이션 + backfill (2026-08-12, 검증 R-4·리뷰 D-4)
--
-- 운영 프로필은 ddl-auto: validate 다. 애플리케이션보다 이 파일을 먼저 적용하지 않으면
-- retry_count 누락으로 기동이 중단된다.
--
-- MySQL 8에는 ADD COLUMN IF NOT EXISTS가 없으므로 information_schema로 존재 여부를 확인한다.
-- 여러 번 실행해도 결과가 같은 멱등 스크립트다.
--
-- 실행 전 백업:
-- mysqldump mlsoft_leave email_history > backup-email-history.sql
-- ============================================================================

-- ── 1부. retry_count 컬럼 추가 ───────────────────────────────────────────────

SET @ddl := IF((SELECT COUNT(*)
                  FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'email_history'
                   AND COLUMN_NAME = 'retry_count') = 0,
               'ALTER TABLE email_history ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER error_message',
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 기존 nullable 컬럼이 수동으로 만들어진 환경까지 보정한다.
UPDATE email_history
   SET retry_count = 0
 WHERE retry_count IS NULL;

-- ── 2부. 재시도 조회 인덱스 추가 ────────────────────────────────────────────

SET @ddl := IF((SELECT COUNT(*)
                  FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'email_history'
                   AND INDEX_NAME = 'idx_email_history_retry') = 0,
               'ALTER TABLE email_history ADD INDEX idx_email_history_retry (status, retry_count, id)',
               'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 확인 쿼리 ────────────────────────────────────────────────────────────────
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME = 'email_history'
--    AND COLUMN_NAME = 'retry_count';
--
-- SHOW INDEX FROM email_history WHERE Key_name = 'idx_email_history_retry';
-- SELECT status, retry_count, COUNT(*) FROM email_history GROUP BY status, retry_count;
