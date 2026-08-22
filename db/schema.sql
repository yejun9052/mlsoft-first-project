-- =====================================================================
-- MLsoft 연차 시스템 — 운영 DB 스키마 (리뷰 O-2)
--
-- 운영 프로필은 ddl-auto: validate 라 Hibernate가 테이블을 만들지 않는다.
-- 이 파일이 최초 1회 스키마를 만드는 유일한 수단이다.
--
-- 적용 경로: docker-compose.prod.yml 이 이 파일을 mysql 컨테이너의
--            /docker-entrypoint-initdb.d/ 에 마운트한다. MySQL은 데이터 볼륨이
--            비어 있을 때만 이 디렉터리를 실행하므로 재기동해도 덮어쓰지 않는다.
--
-- 생성 방법: 임시 DB에 ddl-auto: update 로 앱을 한 번 띄워 엔티티에서 스키마를
--            만든 뒤 덤프했다(개발 DB는 건드리지 않는다).
--   mysql  -u root -e "CREATE DATABASE mlsoft_schemagen ..."
--   (앱을 mlsoft_schemagen 에 --spring.jpa.hibernate.ddl-auto=update 로 1회 기동)
--   mysqldump -u root --no-data --skip-comments --skip-add-drop-table mlsoft_schemagen
--
-- ⚠️ 엔티티를 바꾸면 이 파일도 같이 갱신해야 한다. 안 하면 배포 시 validate가
--    기동을 거부한다(그게 이 설정의 목적이다 — 조용한 ALTER보다 낫다).
--    마이그레이션 도구(Flyway) 전환은 docs/08 Y-6 / docs/12.
--
-- 최종 생성 2026-08-08 — 테이블 15개
--   · 개인 일정: schedule_entries · schedule_dates
--   · 공휴일 holidays.date 에 UNIQUE(uk_holidays_date) — 중복 적재 방지 (리뷰 D-3)
--
-- 2026-08-10 추가 — 테이블 16개
--   · admin_audit_log — 관리자 조작 감사 로그 (리뷰 S-3)
--     같은 절차(임시 DB + ddl-auto: update + mysqldump)로 생성했지만 **전체를 교체하지 않고
--     이 블록만 삽입했다**. 새로 덤프하면 기존 테이블에 무관한 차이가 3종 섞여 들어온다:
--       ① FK 단독 인덱스가 사라진다 — D-1의 복합 인덱스가 FK를 커버하면 MySQL이
--          중복 인덱스를 만들지 않는다. 생성 순서 차이일 뿐 둘 다 유효하다.
--       ② carried_bonus_days·monthly_granted_count의 DEFAULT 절이 사라진다 —
--          이 값은 Hibernate가 아니라 backfill SQL이 넣은 것이다. 지우면 안전망이 없어진다.
--       ③ AUTO_INCREMENT 시작값이 데이터 유무에 따라 달라진다.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `mlsoft_leave`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mlsoft_leave`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  -- 2026-08-16 USER_RESTORED 추가 (backfill-2026-08-16-user-restore.sql)
  -- AdminAction enum에 상수를 넣으면 이 목록도 함께 늘려야 한다 — ddl-auto: update는 기존 ENUM을 넓히지 않는다
  `action` enum('BASE_DAYS_CHANGED','CONFIG_CHANGED','DEPARTMENT_CHANGED','ONBOARDING_APPROVED','ONBOARDING_REJECTED','ROLE_CHANGED','USER_RESTORED','USER_RETIRED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `after_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `before_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` bigint NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_created` (`created_at`),
  KEY `idx_audit_actor_created` (`actor_id`,`created_at`),
  KEY `idx_audit_target_created` (`target_user_id`,`created_at`),
  CONSTRAINT `FK4vvcqcx4rnv9ptoierfcvu96l` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK8uh7kqh3yijgjkelu45upt5tl` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `system_default` bit(1) NOT NULL DEFAULT b'0',
  `leader_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2p51g6b22peoewswi0kgvp0kb` (`leader_id`),
  CONSTRAINT `FK2p51g6b22peoewswi0kgvp0kb` FOREIGN KEY (`leader_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `email_type` enum('LEAVE','NOTICE','REMINDER','WELFARE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('FAILED','PENDING','SENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `from_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKl5edhxdva8d6sdxa70a5cvdo4` (`from_id`),
  KEY `FKokrrh26v7faaux2a2mk7qbsec` (`user_id`),
  KEY `idx_email_history_retry` (`status`,`retry_count`,`id`),
  CONSTRAINT `FKl5edhxdva8d6sdxa70a5cvdo4` FOREIGN KEY (`from_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKokrrh26v7faaux2a2mk7qbsec` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `holidays` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `date` date NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `year` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_holidays_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `leave_action_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `action` enum('APPROVED','CANCELLED','CANCEL_APPROVED','CANCEL_PENDING','CANCEL_REJECTED','PENDING','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `comment` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` bigint NOT NULL,
  `leave_requests_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKepla6r67avn0fthfxru6v3tf6` (`actor_id`),
  KEY `FKut1n87icr6yhvofk9hgx5shl` (`leave_requests_id`),
  KEY `FKoq63m0l95fc0cg5x6ww0c9onu` (`user_id`),
  KEY `idx_leave_history_created` (`created_at`),
  KEY `idx_leave_history_actor_created` (`actor_id`,`created_at`),
  KEY `idx_leave_history_user_created` (`user_id`,`created_at`),
  CONSTRAINT `FKepla6r67avn0fthfxru6v3tf6` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKoq63m0l95fc0cg5x6ww0c9onu` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKut1n87icr6yhvofk9hgx5shl` FOREIGN KEY (`leave_requests_id`) REFERENCES `leave_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `leave_dates` (
  `leave_requests_id` bigint NOT NULL,
  `day` date NOT NULL,
  UNIQUE KEY `uk_leave_dates_request_day` (`leave_requests_id`,`day`),
  KEY `idx_leave_dates_day` (`day`),
  CONSTRAINT `FKsdttlsgxpce8dgyp7tam6tsk5` FOREIGN KEY (`leave_requests_id`) REFERENCES `leave_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `leave_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `annual_leave_days` decimal(4,1) NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `years_of_service` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKipx9754u6une54ugi5ghyqhdg` (`years_of_service`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `leave_policy_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5y8sgsoi3qja3a82dxy8grv7a` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `leave_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `advance_used_days` decimal(4,1) NOT NULL,
  `cancel_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `days` decimal(4,1) NOT NULL,
  `leave_type` enum('ANNUAL','HALF_AM','HALF_PM') COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('APPROVED','CANCELLED','CANCEL_PENDING','PENDING','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `primary_approver_id` bigint NOT NULL,
  `sub_approver_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKforbkctpu3sp6ani01ellbgol` (`primary_approver_id`),
  KEY `FKkjmv5wjgbkwcnvxqkicku6vgw` (`sub_approver_id`),
  KEY `FKh6s8bo5d59oy52b6nxfguf4yx` (`user_id`),
  KEY `idx_leave_requests_user_status` (`user_id`,`status`),
  KEY `idx_leave_requests_primary_status` (`primary_approver_id`,`status`),
  KEY `idx_leave_requests_sub_status` (`sub_approver_id`,`status`),
  CONSTRAINT `FKforbkctpu3sp6ani01ellbgol` FOREIGN KEY (`primary_approver_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKh6s8bo5d59oy52b6nxfguf4yx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKkjmv5wjgbkwcnvxqkicku6vgw` FOREIGN KEY (`sub_approver_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `leave_reset_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `advance_settled` decimal(4,1) NOT NULL,
  `carried_bonus_days` decimal(4,1) NOT NULL DEFAULT '0.0',
  `expired_days` decimal(4,1) NOT NULL,
  `new_base_days` decimal(4,1) NOT NULL,
  `prev_base_days` decimal(4,1) NOT NULL,
  `prev_use_days` decimal(4,1) NOT NULL,
  `reset_date` date NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5xtwcopwf0vymrxo9u9pc5ip3` (`user_id`),
  KEY `idx_leave_reset_history_reset_date` (`reset_date`),
  CONSTRAINT `FK5xtwcopwf0vymrxo9u9pc5ip3` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_dates` (
  `schedule_entry_id` bigint NOT NULL,
  `day` date NOT NULL,
  UNIQUE KEY `uk_schedule_dates_entry_day` (`schedule_entry_id`,`day`),
  KEY `idx_schedule_dates_day` (`day`),
  CONSTRAINT `FKthbmls6l4bp0muhl5ct74vtos` FOREIGN KEY (`schedule_entry_id`) REFERENCES `schedule_entries` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `memo` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `schedule_type` enum('BUSINESS_TRIP','FIELD_WORK','REMOTE','TRAINING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8dju7qu6977w3dts3qx31sn49` (`user_id`),
  CONSTRAINT `FK8dju7qu6977w3dts3qx31sn49` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `advance_days` decimal(4,1) NOT NULL,
  `base_days` decimal(4,1) NOT NULL,
  `birth_day` date DEFAULT NULL,
  `bonus_days` decimal(4,1) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `hire_date` date DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `last_birthday_grant_year` int DEFAULT NULL,
  `last_reset_date` date DEFAULT NULL,
  `monthly_granted_count` int NOT NULL DEFAULT '0',
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  -- 2026-08-16 추가 — 승인 대기 중 입사일 1회 수정 (backfill-2026-08-16-onboarding-revision.sql)
  `onboarding_revised` bit(1) NOT NULL DEFAULT b'0',
  `onboarding_status` enum('COMPLETED','NOT_STARTED','PENDING_APPROVAL') COLLATE utf8mb4_unicode_ci NOT NULL,
  `position` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `retired_at` date DEFAULT NULL,
  `role` enum('EMPLOYEE','SYSTEM_ADMIN','TEAM_LEADER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `update_at` datetime(6) DEFAULT NULL,
  `use_days` decimal(4,1) NOT NULL,
  `version` bigint NOT NULL,
  `department_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  KEY `FKfi832e3qv89fq376fuh8920y4` (`department_id`),
  CONSTRAINT `FKfi832e3qv89fq376fuh8920y4` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `welfare_action_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `action` enum('APPROVED','CANCELLED','CANCEL_APPROVED','CANCEL_PENDING','CANCEL_REJECTED','PENDING','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `comment` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `welfare_request_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoqrrjuo9vaff18it95bknw1kx` (`actor_id`),
  KEY `FK9n165y2oyujt1hmh0iwx4gx5o` (`user_id`),
  KEY `FKlrrdr1u1amvnsgpumyljjxg7p` (`welfare_request_id`),
  KEY `idx_welfare_history_created` (`created_at`),
  KEY `idx_welfare_history_actor_created` (`actor_id`,`created_at`),
  KEY `idx_welfare_history_user_created` (`user_id`,`created_at`),
  CONSTRAINT `FK9n165y2oyujt1hmh0iwx4gx5o` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKlrrdr1u1amvnsgpumyljjxg7p` FOREIGN KEY (`welfare_request_id`) REFERENCES `welfare_requests` (`id`),
  CONSTRAINT `FKoqrrjuo9vaff18it95bknw1kx` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `welfare_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `active` bit(1) NOT NULL,
  `category` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `default_days` decimal(4,1) NOT NULL,
  `default_evidence` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target` enum('CHILD','GRANDPARENT','OTHER','PARENT','SELF','SIBLING','SPOUSE','SPOUSE_GRANDPARENT','SPOUSE_PARENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `welfare_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `add_days` decimal(4,1) NOT NULL,
  `category` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `evidence_guide` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `primary_approver_id` bigint NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('APPROVED','CANCELLED','CANCEL_PENDING','PENDING','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `sub_approver_id` bigint DEFAULT NULL,
  `target` enum('CHILD','GRANDPARENT','OTHER','PARENT','SELF','SIBLING','SPOUSE','SPOUSE_GRANDPARENT','SPOUSE_PARENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `policy_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  -- 2026-08-10 추가 (리뷰 D-2) — 연차 신청과 대칭. 목록 3종이 "누구의 + 어떤 상태" 조합이다
  KEY `idx_welfare_requests_user_status` (`user_id`,`status`),
  KEY `idx_welfare_requests_primary_status` (`primary_approver_id`,`status`),
  KEY `idx_welfare_requests_sub_status` (`sub_approver_id`,`status`),
  KEY `FKc2s5m4i1dcs5v0r14b1n0421r` (`policy_id`),
  KEY `FKfubsoplr3raeot86n9n0nsjev` (`user_id`),
  CONSTRAINT `FKc2s5m4i1dcs5v0r14b1n0421r` FOREIGN KEY (`policy_id`) REFERENCES `welfare_policies` (`id`),
  CONSTRAINT `FKfubsoplr3raeot86n9n0nsjev` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  -- 2026-08-10 추가 (리뷰 D-5) — 승인자가 FK 없는 raw BIGINT였다. 컬럼은 그대로고 제약만 붙었다
  CONSTRAINT `FKn9oejajygjaj2rmrhg28c18l2` FOREIGN KEY (`primary_approver_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK7yale7ten07ng9mnsa0v2c8w1` FOREIGN KEY (`sub_approver_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


