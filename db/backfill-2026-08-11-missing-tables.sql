-- ============================================================================
-- 개인 일정·공휴일 누락 테이블 생성 (2026-08-11)
--
-- 운영 DB는 2026-07-22에 생성됐고, db/schema.sql은 MySQL 데이터 볼륨이
-- 비어 있을 때만 실행된다. 이후 추가된 개인 일정·공휴일 테이블은
-- db/schema.sql에는 반영됐지만 기존 DB용 backfill에는 생성 경로가 없었다.
--
-- 운영 프로필은 Hibernate ddl-auto: validate이므로 애플리케이션이 테이블을
-- 대신 만들지 않는다. 이 파일은 기존 DB에 누락된 다음 세 테이블을 만든다.
--
--   1. holidays
--   2. schedule_entries
--   3. schedule_dates
--
-- DDL은 현재 db/schema.sql과 동일하다. CREATE TABLE IF NOT EXISTS를 사용하므로
-- 재실행해도 기존 테이블과 행을 건드리지 않는다.
--
-- schedule_dates는 schedule_entries를 참조하므로 부모 테이블을 먼저 만든다.
-- FOREIGN_KEY_CHECKS를 끄지 않아 생성 시점부터 참조 무결성을 검증한다.
--
-- 과거 데이터 보정은 하지 않는다. 개인 일정과 공휴일은 신규 기능 데이터이며,
-- 기존 DB에서 소급 복원할 신뢰할 원천이 없기 때문이다.
-- ============================================================================

-- 공휴일은 독립 테이블이다. 날짜 UNIQUE는 재동기화와 동시 적재에 따른
-- 중복 행을 DB에서 마지막으로 차단한다 (리뷰 D-3).
CREATE TABLE IF NOT EXISTS `holidays` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `date` date NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `year` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_holidays_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 일정 본문을 날짜 테이블보다 먼저 만든다. 일정은 연차와 달리 결재·상태 전이·
-- 잔액 차감이 없으므로 status·approver·days 컬럼을 두지 않는다.
CREATE TABLE IF NOT EXISTS `schedule_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `memo` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `schedule_type` enum('BUSINESS_TRIP','FIELD_WORK','REMOTE','TRAINING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8dju7qu6977w3dts3qx31sn49` (`user_id`),
  CONSTRAINT `FK8dju7qu6977w3dts3qx31sn49`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 날짜는 ScheduleEntry의 @ElementCollection 값이다. 독립 생명주기나 대리키를
-- 만들지 않고 (일정, 날짜) UNIQUE를 실질 키로 사용한다.
CREATE TABLE IF NOT EXISTS `schedule_dates` (
  `schedule_entry_id` bigint NOT NULL,
  `day` date NOT NULL,
  UNIQUE KEY `uk_schedule_dates_entry_day` (`schedule_entry_id`,`day`),
  -- 캘린더는 day 범위로 조회한다. 위 UNIQUE는 schedule_entry_id가 선두라
  -- day 단독 범위 검색을 처리하지 못하므로 별도 인덱스가 필요하다 (리뷰 D-1).
  KEY `idx_schedule_dates_day` (`day`),
  CONSTRAINT `FKthbmls6l4bp0muhl5ct74vtos`
    FOREIGN KEY (`schedule_entry_id`) REFERENCES `schedule_entries` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 확인 쿼리 ───────────────────────────────────────────────────────────────
-- 세 테이블이 모두 존재하는지 확인한다. 결과는 각각 1행이어야 한다.
--
-- SELECT TABLE_NAME
--   FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('holidays', 'schedule_entries', 'schedule_dates')
--  ORDER BY TABLE_NAME;
--
-- 공휴일 UNIQUE와 일정 날짜 인덱스를 확인한다.
--
-- SELECT TABLE_NAME,
--        INDEX_NAME,
--        NON_UNIQUE,
--        GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_in_order
--   FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('holidays', 'schedule_entries', 'schedule_dates')
--  GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
--  ORDER BY TABLE_NAME, INDEX_NAME;
--
-- 일정 FK 두 개를 확인한다.
--
-- SELECT TABLE_NAME,
--        COLUMN_NAME,
--        CONSTRAINT_NAME,
--        REFERENCED_TABLE_NAME,
--        REFERENCED_COLUMN_NAME
--   FROM information_schema.KEY_COLUMN_USAGE
--  WHERE TABLE_SCHEMA = DATABASE()
--    AND TABLE_NAME IN ('schedule_entries', 'schedule_dates')
--    AND REFERENCED_TABLE_NAME IS NOT NULL
--  ORDER BY TABLE_NAME, COLUMN_NAME;
