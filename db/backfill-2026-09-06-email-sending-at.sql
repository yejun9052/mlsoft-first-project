-- ============================================================================
-- 이메일 SENDING 선점 시각 추가 (2026-09-06, docs/설계-초안/연차-소진-안내-메일-설계-2026-08-28.md §5)
--
-- 운영 프로필은 ddl-auto: validate다. Hibernate가 컬럼을 만들어 주지 않으므로
-- 이미 데이터가 있는 DB에는 이 backfill을 실행해야 한다. db/schema.sql은 빈 DB를
-- 처음 만들 때만 적용된다.
--
-- 기존 SENDING 행의 sending_at은 보정하지 않는다. 값이 NULL인 과거 행은 애플리케이션의
-- 복구 조건이 함께 처리한다.
--
-- 멱등하다 — MySQL 8에 ADD COLUMN IF NOT EXISTS가 없으므로 information_schema로 확인한다.
-- 실행 전 백업: mysqldump mlsoft_leave email_history > backup.sql
-- ============================================================================

SET @ddl := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'email_history'
                   AND COLUMN_NAME = 'sending_at') = 0,
               'ALTER TABLE email_history ADD COLUMN sending_at DATETIME(6) NULL',
               'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
