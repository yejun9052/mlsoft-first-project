-- 2026-08-21 · 부서에 "시스템 기본" 표시 추가 (docs/12 B-9)
--
-- 왜 필요한가
--   DataInitializer가 "미배정"이라는 **이름의 실제 부서 행**을 만들고 신규 자동 가입자를
--   거기 배속한다. 그래서 실계정 중 department_id가 NULL인 사람이 사실상 없고,
--   "부서 없음"을 조건으로 삼은 화면 분기가 전부 죽어 있었다.
--   이름으로 판별하면 관리자가 이름을 바꾸는 순간 조용히 깨지므로 구조적 표시를 둔다.
--
-- 멱등하다. 여러 번 돌려도 안전하다.

-- 1) 컬럼 추가 (MySQL 8에는 ADD COLUMN IF NOT EXISTS가 없다)
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'department'
     AND COLUMN_NAME = 'system_default'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `department` ADD COLUMN `system_default` bit(1) NOT NULL DEFAULT b''0'' AFTER `parent_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 기존 "미배정" 부서에 표시를 붙인다
--    이미 표시된 부서가 있으면 건드리지 않는다 — 기본 부서는 하나여야 한다.
UPDATE `department`
   SET `system_default` = b'1'
 WHERE `name` = '미배정'
   AND `system_default` = b'0'
   AND NOT EXISTS (
     SELECT 1 FROM (SELECT 1 FROM `department` WHERE `system_default` = b'1' LIMIT 1) AS marked
   );

-- 3) 확인 — 정확히 1행이어야 한다 (0이면 앱 첫 기동이 보정한다)
SELECT id, name, system_default FROM `department` WHERE `system_default` = b'1';
