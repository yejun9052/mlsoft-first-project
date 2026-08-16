-- ============================================================================
-- 퇴직 복구 — admin_audit_log.action ENUM에 USER_RESTORED 추가 (2026-08-16)
--
-- **왜 필요한가**: action은 MySQL ENUM 컬럼이고, Java의 AdminAction에 상수를 추가해도
-- `ddl-auto: update`는 **기존 컬럼 정의를 바꾸지 않는다**. 컬럼 추가만 한다.
-- 그래서 값을 넣는 순간 `Data truncated for column 'action' at row 1`로 터진다
-- (2026-08-16 로컬에서 실제로 겪었다 — 퇴직 복구를 누르자 500).
--
-- 테스트(H2)는 매번 엔티티에서 스키마를 새로 만들므로 이 결함을 **절대 못 잡는다.**
-- enum 상수를 추가할 때는 반드시 이 파일 같은 ALTER를 함께 써야 한다.
--
-- 운영은 ddl-auto: validate라 이 스크립트를 **배포 전에 먼저** 실행해야 한다.
-- (validate는 ENUM 값 목록까지는 검사하지 않으므로 기동은 되지만, 첫 복구에서 500이 난다.
--  즉 여기서는 fail-fast가 걸리지 않는다 — 더 조용히 터진다.)
--
-- 멱등하다 — 이미 값이 있으면 건너뛴다.
--
-- 실행 전 백업: mysqldump mlsoft_leave admin_audit_log > backup-audit.sql
-- ============================================================================

SET @ddl := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'admin_audit_log'
        AND COLUMN_NAME = 'action'
        AND COLUMN_TYPE LIKE '%USER_RESTORED%') = 0,
    "ALTER TABLE admin_audit_log MODIFY COLUMN `action` enum('BASE_DAYS_CHANGED','CONFIG_CHANGED','DEPARTMENT_CHANGED','ONBOARDING_APPROVED','ONBOARDING_REJECTED','ROLE_CHANGED','USER_RESTORED','USER_RETIRED') COLLATE utf8mb4_unicode_ci NOT NULL",
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 확인 쿼리 (실행 후 눈으로 볼 것) ────────────────────────────────────────
-- SELECT COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME = 'admin_audit_log' AND COLUMN_NAME = 'action';
-- USER_RESTORED가 목록에 있어야 한다.
