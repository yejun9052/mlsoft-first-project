-- ============================================================================
-- 운영 backfill 사전검증 (읽기 전용, 2026-08-23)
--
-- 이 파일은 information_schema와 필요한 현재 데이터의 SELECT를 실행한다.
-- SET NAMES·사용자 변수·PREPARE/EXECUTE는 현재 세션에만 적용되며,
-- 스키마·업무 데이터는 변경하지 않는다.
--
-- 실행 예:
--   mysql --default-character-set=utf8mb4 --database=mlsoft_leave < preflight-2026-08-23-operational.sql
--
-- 판정 규칙:
--   PRE 항목이 없으면 STOP — backfill 전에 준비해야 한다.
--   POST 항목이 없으면 PENDING — 해당 backfill이 만들 대상이므로 정상이다.
--   POST 항목이 있으면 ALREADY_APPLIED — 재실행 전 상태로 정상이다.
--   MISMATCH / DUPLICATE는 중단 후 staging에서 원인을 확인한다.
--   NAME_MISMATCH는 FK 대상이 맞으면 진행 가능하지만 배포 기록에 남긴다.
--
-- db/schema.sql 전체를 운영 DB에 재실행하는 용도로 사용하지 않는다.
-- ============================================================================

-- 한글 리터럴 비교·출력과 information_schema 컬럼 비교를 고정한다.
SET NAMES utf8mb4;

-- 0. 배포 기록용 실행 메타데이터와 연결 대상 확인.
SELECT
    'META' AS check_group,
    NOW() AS run_at,
    VERSION() AS server_version,
    @@sql_mode AS sql_mode,
    @@innodb_strict_mode AS innodb_strict_mode,
    DATABASE() AS target_schema,
    IF(DATABASE() = 'mlsoft_leave', 'OK', 'STOP') AS result;

-- 1. 운영 스키마 전체 테이블 inventory.
-- POST 테이블이 PENDING인 것은 해당 단계가 아직 실행되지 않았다는 뜻이다.
SELECT
    'TABLE' AS check_group,
    required.table_name AS check_name,
    required.expected_state AS expected_state,
    CASE
        WHEN actual.TABLE_NAME IS NULL AND required.expected_state = 'PRE' THEN 'STOP'
        WHEN actual.TABLE_NAME IS NULL THEN 'PENDING'
        WHEN required.expected_state = 'PRE' THEN 'OK'
        ELSE 'ALREADY_APPLIED'
    END AS result,
    required.required_by AS required_by
FROM (
    SELECT 'admin_audit_log' AS table_name, 'POST:#6' AS expected_state, 'audit-log' AS required_by
    UNION ALL SELECT 'department', 'PRE', 'O-3 / department-system-default'
    UNION ALL SELECT 'email_history', 'PRE', 'email / retry backfill prerequisite'
    UNION ALL SELECT 'holidays', 'POST:#3', 'missing-tables'
    UNION ALL SELECT 'leave_action_history', 'PRE', 'query-indexes'
    UNION ALL SELECT 'leave_dates', 'PRE', 'query-indexes'
    UNION ALL SELECT 'leave_policy', 'PRE', 'application schema'
    UNION ALL SELECT 'leave_policy_config', 'PRE', 'application schema'
    UNION ALL SELECT 'leave_requests', 'PRE', 'query-indexes'
    UNION ALL SELECT 'leave_reset_history', 'PRE', 'scheduler / query-indexes'
    UNION ALL SELECT 'schedule_dates', 'POST:#3', 'missing-tables / query-indexes'
    UNION ALL SELECT 'schedule_entries', 'POST:#3', 'missing-tables'
    UNION ALL SELECT 'users', 'PRE', 'onboarding / scheduler / revision'
    UNION ALL SELECT 'welfare_action_history', 'PRE', 'query-indexes'
    UNION ALL SELECT 'welfare_policies', 'PRE', 'application schema / welfare FK target'
    UNION ALL SELECT 'welfare_requests', 'PRE', 'welfare-fk / query-indexes'
) AS required
LEFT JOIN information_schema.TABLES AS actual
       ON actual.TABLE_SCHEMA = DATABASE()
      AND CONVERT(actual.TABLE_NAME USING utf8mb4) = required.table_name
      AND actual.TABLE_TYPE = 'BASE TABLE'
ORDER BY required.table_name;

-- 2. 컬럼 선행조건과 backfill 산출물 inventory.
SELECT
    'COLUMN' AS check_group,
    required.table_name AS table_name,
    required.column_name AS check_name,
    required.expected_state AS expected_state,
    CASE
        WHEN actual.COLUMN_NAME IS NULL AND required.expected_state = 'PRE' THEN 'STOP'
        WHEN actual.COLUMN_NAME IS NULL THEN 'PENDING'
        WHEN required.expected_state = 'PRE' THEN 'OK'
        ELSE 'ALREADY_APPLIED'
    END AS result,
    actual.COLUMN_TYPE AS observed_type,
    required.required_by AS required_by
FROM (
    SELECT 'admin_audit_log' AS table_name, 'target_label' AS column_name, 'POST:#6' AS expected_state, '#6 CREATE TABLE IF NOT EXISTS 구조 확인' AS required_by
    UNION ALL SELECT 'holidays', 'year', 'POST:#3', '#3 CREATE TABLE IF NOT EXISTS 구조 확인'
    UNION ALL SELECT 'schedule_entries', 'schedule_type', 'POST:#3', '#3 CREATE TABLE IF NOT EXISTS 구조 확인'
    UNION ALL SELECT 'department', 'active', 'PRE', '#1 O-3 UPDATE 선행'
    UNION ALL SELECT 'department', 'name', 'PRE', '§7 미배정 부서 카운트 선행'
    UNION ALL SELECT 'department', 'parent_id', 'PRE', '#11 AFTER parent_id 선행'
    UNION ALL SELECT 'department', 'system_default', 'POST:#11', '#11 department-system-default'
    UNION ALL SELECT 'email_history', 'error_message', 'PRE', '#8 AFTER error_message 선행'
    UNION ALL SELECT 'email_history', 'retry_count', 'POST:#8', '#8 email retry'
    UNION ALL SELECT 'leave_reset_history', 'carried_bonus_days', 'POST:#5', '#5 scheduler'
    UNION ALL SELECT 'users', 'last_birthday_grant_year', 'POST:#5', '#5 scheduler'
    UNION ALL SELECT 'users', 'last_reset_date', 'PRE', 'annual reset scheduler'
    UNION ALL SELECT 'users', 'monthly_granted_count', 'POST:#5', '#5 scheduler'
    UNION ALL SELECT 'users', 'name', 'PRE', '#9 AFTER name 선행'
    UNION ALL SELECT 'users', 'onboarding_revised', 'POST:#9', '#9 onboarding revision'
    UNION ALL SELECT 'users', 'onboarding_status', 'POST:#2', '#2 onboarding approval'
    UNION ALL SELECT 'users', 'advance_days', 'PRE', '§7 파생값 불변식 선행'
    UNION ALL SELECT 'users', 'base_days', 'PRE', '§7 파생값 불변식 선행'
    UNION ALL SELECT 'users', 'bonus_days', 'PRE', '§7 파생값 불변식 선행'
    UNION ALL SELECT 'users', 'hire_date', 'PRE', '#2 2부 / #5 2부 / §7 선행'
    UNION ALL SELECT 'users', 'use_days', 'PRE', '§7 파생값 불변식 선행'
    UNION ALL SELECT 'welfare_requests', 'primary_approver_id', 'PRE', '#7 FK source column'
    UNION ALL SELECT 'welfare_requests', 'sub_approver_id', 'PRE', '#7 FK source column'
) AS required
LEFT JOIN information_schema.COLUMNS AS actual
       ON actual.TABLE_SCHEMA = DATABASE()
      AND CONVERT(actual.TABLE_NAME USING utf8mb4) = required.table_name
      AND CONVERT(actual.COLUMN_NAME USING utf8mb4) = required.column_name
ORDER BY required.table_name, required.column_name;

-- 3. query-indexes·missing-tables·welfare-fk·email이 만드는 인덱스.
-- UNIQUE 여부와 컬럼 순서를 모두 비교한다.
SELECT
    'INDEX' AS check_group,
    required.table_name AS table_name,
    required.index_name AS check_name,
    required.expected_state AS expected_state,
    CASE
        WHEN COUNT(actual.INDEX_NAME) = 0 AND required.expected_state = 'PRE' THEN 'STOP'
        WHEN COUNT(actual.INDEX_NAME) = 0 THEN 'PENDING'
        WHEN GROUP_CONCAT(CONVERT(actual.COLUMN_NAME USING utf8mb4) ORDER BY actual.SEQ_IN_INDEX SEPARATOR ',')
                 = required.expected_columns
             AND MIN(actual.NON_UNIQUE) = required.expected_non_unique
            THEN 'ALREADY_APPLIED'
        ELSE 'MISMATCH'
    END AS result,
    required.expected_columns AS expected_columns,
    required.expected_non_unique AS expected_non_unique,
    GROUP_CONCAT(CONVERT(actual.COLUMN_NAME USING utf8mb4) ORDER BY actual.SEQ_IN_INDEX SEPARATOR ',') AS observed_columns,
    MIN(actual.NON_UNIQUE) AS observed_non_unique,
    required.required_by AS required_by
FROM (
    SELECT 'email_history' AS table_name, 'idx_email_history_retry' AS index_name, 'status,retry_count,id' AS expected_columns, 1 AS expected_non_unique, 'POST:#8' AS expected_state, '#8 email' AS required_by
    UNION ALL SELECT 'admin_audit_log', 'idx_audit_actor_created', 'actor_id,created_at', 1, 'POST:#6', '#6 audit-log'
    UNION ALL SELECT 'admin_audit_log', 'idx_audit_created', 'created_at', 1, 'POST:#6', '#6 audit-log'
    UNION ALL SELECT 'admin_audit_log', 'idx_audit_target_created', 'target_user_id,created_at', 1, 'POST:#6', '#6 audit-log'
    UNION ALL SELECT 'holidays', 'uk_holidays_date', 'date', 0, 'POST:#3', '#3 missing-tables'
    UNION ALL SELECT 'leave_action_history', 'idx_leave_history_actor_created', 'actor_id,created_at', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'leave_action_history', 'idx_leave_history_created', 'created_at', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'leave_action_history', 'idx_leave_history_user_created', 'user_id,created_at', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'leave_dates', 'idx_leave_dates_day', 'day', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'leave_requests', 'idx_leave_requests_primary_status', 'primary_approver_id,status', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'leave_requests', 'idx_leave_requests_sub_status', 'sub_approver_id,status', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'leave_requests', 'idx_leave_requests_user_status', 'user_id,status', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'leave_reset_history', 'idx_leave_reset_history_reset_date', 'reset_date', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'schedule_dates', 'idx_schedule_dates_day', 'day', 1, 'POST:#3/#4', '#3 missing-tables / #4 query-indexes'
    UNION ALL SELECT 'schedule_dates', 'uk_schedule_dates_entry_day', 'schedule_entry_id,day', 0, 'POST:#3', '#3 missing-tables'
    UNION ALL SELECT 'welfare_action_history', 'idx_welfare_history_actor_created', 'actor_id,created_at', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'welfare_action_history', 'idx_welfare_history_created', 'created_at', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'welfare_action_history', 'idx_welfare_history_user_created', 'user_id,created_at', 1, 'POST:#4', '#4 query-indexes'
    UNION ALL SELECT 'welfare_requests', 'idx_welfare_requests_primary_status', 'primary_approver_id,status', 1, 'POST:#7', '#7 welfare-fk-indexes'
    UNION ALL SELECT 'welfare_requests', 'idx_welfare_requests_sub_status', 'sub_approver_id,status', 1, 'POST:#7', '#7 welfare-fk-indexes'
    UNION ALL SELECT 'welfare_requests', 'idx_welfare_requests_user_status', 'user_id,status', 1, 'POST:#7', '#7 welfare-fk-indexes'
) AS required
LEFT JOIN information_schema.STATISTICS AS actual
       ON actual.TABLE_SCHEMA = DATABASE()
      AND CONVERT(actual.TABLE_NAME USING utf8mb4) = required.table_name
      AND CONVERT(actual.INDEX_NAME USING utf8mb4) = required.index_name
GROUP BY required.table_name,
         required.index_name,
         required.expected_state,
         required.expected_columns,
         required.expected_non_unique,
         required.required_by
ORDER BY required.table_name, required.index_name;

-- 4. validate가 잡지 못하는 admin_audit_log.action ENUM 확장.
SELECT
    'ENUM' AS check_group,
    'admin_audit_log.action' AS check_name,
    'POST:#10' AS expected_state,
    CASE
        WHEN actual.COLUMN_NAME IS NULL THEN 'PENDING'
        WHEN actual.COLUMN_TYPE = 'enum(''BASE_DAYS_CHANGED'',''CONFIG_CHANGED'',''DEPARTMENT_CHANGED'',''ONBOARDING_APPROVED'',''ONBOARDING_REJECTED'',''ROLE_CHANGED'',''USER_RESTORED'',''USER_RETIRED'')'
            THEN 'ALREADY_APPLIED'
        ELSE 'MISMATCH'
    END AS result,
    actual.COLUMN_TYPE AS observed_type,
    CASE
        WHEN actual.COLUMN_NAME IS NULL THEN 'COLUMN_MISSING'
        WHEN actual.COLUMN_TYPE LIKE '%USER_RESTORED%' THEN 'CONTAINS_USER_RESTORED'
        ELSE 'MISSING_USER_RESTORED'
    END AS enum_note,
    'backfill-2026-08-16-user-restore.sql' AS required_by
FROM (
    SELECT 'admin_audit_log' AS table_name, 'action' AS column_name
) AS required
LEFT JOIN information_schema.COLUMNS AS actual
       ON actual.TABLE_SCHEMA = DATABASE()
      AND CONVERT(actual.TABLE_NAME USING utf8mb4) = required.table_name
      AND CONVERT(actual.COLUMN_NAME USING utf8mb4) = required.column_name;

-- 5. 복리후생 승인자 FK. 중복·이름 불일치·다른 참조 대상을 구분한다.
SELECT
    'FOREIGN_KEY' AS check_group,
    required.table_name AS table_name,
    required.column_name AS check_name,
    required.expected_state AS expected_state,
    CASE
        WHEN COUNT(actual.CONSTRAINT_NAME) = 0 AND required.expected_state = 'PRE' THEN 'STOP'
        WHEN COUNT(actual.CONSTRAINT_NAME) = 0 THEN 'PENDING'
        WHEN COUNT(actual.CONSTRAINT_NAME) > 1 THEN 'DUPLICATE'
        WHEN CONVERT(MIN(actual.REFERENCED_TABLE_NAME) USING utf8mb4) <> required.referenced_table
          OR CONVERT(MIN(actual.REFERENCED_COLUMN_NAME) USING utf8mb4) <> required.referenced_column THEN 'MISMATCH'
        WHEN CONVERT(MIN(actual.CONSTRAINT_NAME) USING utf8mb4) <> required.expected_name THEN 'NAME_MISMATCH'
        ELSE 'ALREADY_APPLIED'
    END AS result,
    COUNT(actual.CONSTRAINT_NAME) AS observed_fk_count,
    GROUP_CONCAT(actual.CONSTRAINT_NAME ORDER BY actual.CONSTRAINT_NAME) AS observed_constraint,
    required.expected_name AS expected_constraint,
    required.required_by AS required_by
FROM (
    SELECT 'welfare_requests' AS table_name, 'primary_approver_id' AS column_name, 'users' AS referenced_table, 'id' AS referenced_column, 'FKn9oejajygjaj2rmrhg28c18l2' AS expected_name, 'POST:#7' AS expected_state, '#7 welfare-fk-indexes' AS required_by
    UNION ALL SELECT 'welfare_requests', 'sub_approver_id', 'users', 'id', 'FK7yale7ten07ng9mnsa0v2c8w1', 'POST:#7', '#7 welfare-fk-indexes'
) AS required
LEFT JOIN information_schema.KEY_COLUMN_USAGE AS actual
       ON actual.TABLE_SCHEMA = DATABASE()
      AND CONVERT(actual.TABLE_NAME USING utf8mb4) = required.table_name
      AND CONVERT(actual.COLUMN_NAME USING utf8mb4) = required.column_name
      AND actual.REFERENCED_TABLE_NAME IS NOT NULL
      AND actual.REFERENCED_TABLE_SCHEMA = DATABASE()
GROUP BY required.table_name,
         required.column_name,
         required.referenced_table,
         required.referenced_column,
         required.expected_name,
         required.expected_state,
         required.required_by
ORDER BY required.column_name;

-- 6. #7이 SIGNAL 45000으로 중단시키는 승인자 고아 행을 미리 확인한다.
-- PRE 테이블·컬럼이 없으면 SKIPPED 한 행을 반환하고 §1·§2의 STOP을 먼저 해결하게 한다.
SET @orphan_ready := (
    (SELECT COUNT(*)
       FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME IN ('welfare_requests', 'users')
        AND TABLE_TYPE = 'BASE TABLE') = 2
    AND
    (SELECT COUNT(*)
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'welfare_requests'
        AND COLUMN_NAME IN ('primary_approver_id', 'sub_approver_id')) = 2
);
SET @sql := IF(@orphan_ready,
    'SELECT ''DATA'' AS check_group, ''welfare approver orphans'' AS check_name,
            COUNT(*) AS observed_count, IF(COUNT(*) = 0, ''OK'', ''STOP'') AS result,
            ''#7이 SIGNAL 45000으로 중단된다 — 먼저 고아 행을 고칠 것'' AS rule
       FROM welfare_requests AS wr
       LEFT JOIN users AS up ON up.id = wr.primary_approver_id
       LEFT JOIN users AS us ON us.id = wr.sub_approver_id
      WHERE up.id IS NULL
         OR (wr.sub_approver_id IS NOT NULL AND us.id IS NULL)',
    'SELECT ''DATA'' AS check_group, ''welfare approver orphans'' AS check_name,
            NULL AS observed_count, ''SKIPPED_PRE_MISSING'' AS result,
            ''PRE 테이블·컬럼 부재 — §1·§2의 STOP을 먼저 해결한 뒤 재실행'' AS rule');
PREPARE preflight_stmt FROM @sql;
EXECUTE preflight_stmt;
DEALLOCATE PREPARE preflight_stmt;

-- 7. backfill DML 영향량을 실행 전에 확인한다. 아래 결과는 판단 자료이며
-- UPDATE를 대신하지 않는다. 각 SELECT를 독립 실행해 한 객체의 부재가 다른 결과를 가리지 않게 한다.
SET @users_hire_ready := (
    EXISTS (SELECT 1 FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND TABLE_TYPE = 'BASE TABLE')
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'hire_date') = 1
);
SET @sql := IF(@users_hire_ready,
    'SELECT ''DATA'' AS check_group, ''users without hire_date'' AS check_name,
            COUNT(*) AS observed_count, ''review before #2'' AS result
       FROM users WHERE hire_date IS NULL',
    'SELECT ''DATA'' AS check_group, ''users without hire_date'' AS check_name,
            NULL AS observed_count, ''SKIPPED_PRE_MISSING'' AS result');
PREPARE preflight_stmt FROM @sql;
EXECUTE preflight_stmt;
DEALLOCATE PREPARE preflight_stmt;

SET @sql := IF(@users_hire_ready,
    'SELECT ''DATA'' AS check_group, ''new-hires for monthly_granted_count'' AS check_name,
            COUNT(*) AS observed_count,
            ''review before #5 (상한값 — 실제 변경 행은 이보다 적거나 같다)'' AS result
       FROM users WHERE hire_date > DATE_SUB(CURDATE(), INTERVAL 1 YEAR)',
    'SELECT ''DATA'' AS check_group, ''new-hires for monthly_granted_count'' AS check_name,
            NULL AS observed_count, ''SKIPPED_PRE_MISSING'' AS result');
PREPARE preflight_stmt FROM @sql;
EXECUTE preflight_stmt;
DEALLOCATE PREPARE preflight_stmt;

SET @department_name_ready := (
    EXISTS (SELECT 1 FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'department' AND TABLE_TYPE = 'BASE TABLE')
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'department' AND COLUMN_NAME = 'name') = 1
);
SET @sql := IF(@department_name_ready,
    'SELECT ''DATA'' AS check_group, ''department named unassigned'' AS check_name,
            COUNT(*) AS observed_count, ''review before #11'' AS result
       FROM department WHERE name = ''미배정''',
    'SELECT ''DATA'' AS check_group, ''department named unassigned'' AS check_name,
            NULL AS observed_count, ''SKIPPED_PRE_MISSING'' AS result');
PREPARE preflight_stmt FROM @sql;
EXECUTE preflight_stmt;
DEALLOCATE PREPARE preflight_stmt;

SET @department_active_ready := (
    EXISTS (SELECT 1 FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'department' AND TABLE_TYPE = 'BASE TABLE')
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'department' AND COLUMN_NAME = 'active') = 1
);
SET @sql := IF(@department_active_ready,
    'SELECT ''DATA'' AS check_group, ''department inactive rows'' AS check_name,
            COUNT(*) AS observed_count, ''review before #1'' AS result
       FROM department WHERE active = 0',
    'SELECT ''DATA'' AS check_group, ''department inactive rows'' AS check_name,
            NULL AS observed_count, ''SKIPPED_PRE_MISSING'' AS result');
PREPARE preflight_stmt FROM @sql;
EXECUTE preflight_stmt;
DEALLOCATE PREPARE preflight_stmt;

SET @advance_ready := (
    EXISTS (SELECT 1 FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND TABLE_TYPE = 'BASE TABLE')
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
            AND COLUMN_NAME IN ('advance_days', 'use_days', 'base_days', 'bonus_days')) = 4
);
SET @sql := IF(@advance_ready,
    'SELECT ''DATA'' AS check_group, ''advance_days invariant mismatches'' AS check_name,
            COUNT(*) AS observed_count, ''review before #1'' AS result
       FROM users
      WHERE advance_days <> GREATEST(0, use_days - base_days - COALESCE(bonus_days, 0))',
    'SELECT ''DATA'' AS check_group, ''advance_days invariant mismatches'' AS check_name,
            NULL AS observed_count, ''SKIPPED_PRE_MISSING'' AS result');
PREPARE preflight_stmt FROM @sql;
EXECUTE preflight_stmt;
DEALLOCATE PREPARE preflight_stmt;

-- `department.system_default`는 #11이 만드는 POST 컬럼이라 여기서 조회하지 않는다.
-- #11 파일 마지막 확인 SELECT와 post-backfill preflight에서 확인한다.
