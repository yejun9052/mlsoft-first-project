-- ============================================================================
-- 사원 직급(job_grade) 컬럼 추가 (2026-09-08)
--
-- 안 돌리면 무엇이 깨지는가:
--   1) 운영 DB에 users.job_grade가 없어 운영 프로필(ddl-auto: validate) 기동이 실패한다.
--   2) 온보딩·내 정보에서 직급을 저장하는 요청이 컬럼 누락으로 실패한다.
--   3) 기존 사원은 직급을 입력하지 않은 상태(NULL)로 유지되며, 별도 데이터 보정은 하지 않는다.
--
-- 직급은 자유 입력 문자열이며 ENUM 변경은 없다.
-- 멱등하다 — MySQL 8에서 ADD COLUMN IF NOT EXISTS를 사용하지 않고
-- information_schema 조건부 DDL을 사용한다.
-- 실행 전 백업: mysqldump mlsoft_leave users > backup-before-job-grade.sql
-- ============================================================================

SET @ddl := IF(
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'users'
        AND COLUMN_NAME = 'job_grade') = 0,
    'ALTER TABLE `users` ADD COLUMN `job_grade` VARCHAR(50) NULL AFTER `position`',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 확인 쿼리 (실행 후 눈으로 확인할 것)
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME = 'users'
--    AND COLUMN_NAME = 'job_grade';
