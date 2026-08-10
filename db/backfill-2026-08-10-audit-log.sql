-- ============================================================================
-- 관리자 조작 감사 로그 테이블 신설 (2026-08-10, 리뷰 S-3)
--
-- ⚠️ **배포 전에 반드시 먼저 실행할 것.** 운영은 `ddl-auto: validate`라
--    이 테이블이 없으면 애플리케이션이 기동 단계에서 멈춘다 (의도된 동작 — O-2).
--
-- 멱등하다 — CREATE TABLE IF NOT EXISTS 하나뿐이라 여러 번 실행해도 안전하고,
-- 기존 행을 건드리지 않는다. 다른 backfill과 달리 **2부(값 보정)가 없다**:
-- 감사 로그는 append-only이고 과거 조작에 대한 기록은 애초에 존재하지 않는다
-- (그게 S-3의 결함이었다). 소급 생성하면 없는 사실을 기록으로 만드는 것이라
-- 하지 않는다 — 이 테이블의 첫 행은 배포 후 첫 관리자 조작이다.
--
-- DDL은 임시 DB에 ddl-auto: update로 앱을 한 번 띄워 Hibernate가 만든 것을
-- 그대로 옮겼다 (db/schema.sql 헤더의 절차와 같다). 손으로 쓰면 enum 목록·
-- FK 이름이 어긋나 validate가 거부한다.
-- ============================================================================

CREATE TABLE IF NOT EXISTS `admin_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `action` enum('BASE_DAYS_CHANGED','CONFIG_CHANGED','DEPARTMENT_CHANGED','ONBOARDING_APPROVED','ONBOARDING_REJECTED','ROLE_CHANGED','USER_RETIRED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `after_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `before_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` bigint NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  -- 목록이 created_at 내림차순이라 필터 컬럼과 묶어 정렬까지 인덱스로 처리한다.
  -- 이 두 복합 인덱스가 FK 컬럼을 왼쪽 접두로 커버하므로 MySQL이 FK 단독 인덱스를
  -- 따로 만들지 않는다 (leave_action_history와 같은 근거 — 리뷰 D-1).
  KEY `idx_audit_created` (`created_at`),
  KEY `idx_audit_actor_created` (`actor_id`,`created_at`),
  KEY `idx_audit_target_created` (`target_user_id`,`created_at`),
  -- target_user_id는 NULL 허용 — 시스템 설정 변경처럼 대상 사원이 없는 조작이 있다
  CONSTRAINT `FK4vvcqcx4rnv9ptoierfcvu96l` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK8uh7kqh3yijgjkelu45upt5tl` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 확인 쿼리 ───────────────────────────────────────────────────────────────
-- SELECT COUNT(*) AS '테이블 존재(1이어야 함)'
--   FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_audit_log';
--
-- 배포 후 첫 조작이 실제로 기록되는지:
-- SELECT l.created_at, l.action, a.name AS actor, l.target_label, l.before_value, l.after_value
--   FROM admin_audit_log l JOIN users a ON a.id = l.actor_id
--  ORDER BY l.id DESC LIMIT 20;
