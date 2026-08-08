-- ============================================================================
-- 조회 인덱스 추가 (2026-08-08, 리뷰 D-1)
--
-- 인덱스 선언이 0건이었다. Hibernate의 `ddl-auto: validate`는 **인덱스를 검사하지 않으므로**
-- 이 파일을 실행하지 않아도 기동은 된다 — 조용히 느려질 뿐이다. 그래서 컬럼 마이그레이션과
-- 달리 배포를 막지 않지만, 데이터가 쌓이기 시작한 지금 함께 실행하는 것이 좋다.
--
-- 멱등하다 — information_schema로 이름 존재 여부를 보고 건너뛴다.
-- 온라인 DDL이라 MySQL 8에서 테이블을 잠그지 않지만, 트래픽이 적을 때 실행할 것.
--
-- ── 넣지 않은 것과 이유 ──────────────────────────────────────────────────────
-- * users: 행이 사원 수(수십~수백)라 풀스캔이 밀리초 미만이고, 연차 신청·승인·취소마다
--   UPDATE되는 테이블이다. 하루 한 번 도는 스케줄러를 위해 인덱스를 얹으면 손해다.
--   사원이 수천 명이 되면 (is_active, onboarding_status, last_reset_date)부터 재검토할 것.
-- * department / leave_policy / leave_policy_config: 행이 수십 개로 끝나는 마스터.
--   기존 UNIQUE(name, years_of_service)가 실질 조회를 이미 처리한다.
-- * action 컬럼: 값이 7개뿐이라 선택도가 낮다. 위 인덱스로 좁힌 뒤 걸러도 충분하다.
-- * name·email·category의 LIKE '%키워드%' 검색: B-tree의 왼쪽 접두 검색을 못 쓴다.
-- * FK 단독 인덱스: MySQL이 FK마다 자동 생성한 것이 이미 있다.
-- * email_history: 조회 기능 자체가 아직 없어 쿼리 근거가 없다.
-- ============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_index_if_missing$$

CREATE PROCEDURE add_index_if_missing(
    IN target_table VARCHAR(64),
    IN target_index VARCHAR(64),
    IN ddl_statement TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND INDEX_NAME = target_index
    ) THEN
        SET @index_ddl = ddl_statement;
        PREPARE index_stmt FROM @index_ddl;
        EXECUTE index_stmt;
        DEALLOCATE PREPARE index_stmt;
    END IF;
END$$

DELIMITER ;

-- ── 연차 신청 — 목록 3종이 "누구의 + 어떤 상태" 조합이다 ────────────────────
-- 내 신청(user), 결재 대기(primary·sub 승인자). FK 자동 인덱스는 컬럼 하나뿐이라
-- 상태까지 걸러 주지 못한다.
CALL add_index_if_missing('leave_requests', 'idx_leave_requests_user_status',
    'CREATE INDEX idx_leave_requests_user_status ON leave_requests (user_id, status)');
CALL add_index_if_missing('leave_requests', 'idx_leave_requests_primary_status',
    'CREATE INDEX idx_leave_requests_primary_status ON leave_requests (primary_approver_id, status)');
CALL add_index_if_missing('leave_requests', 'idx_leave_requests_sub_status',
    'CREATE INDEX idx_leave_requests_sub_status ON leave_requests (sub_approver_id, status)');

-- ── 날짜 테이블 — 캘린더 범위 조회 + 리셋의 이월분 집계가 day로 스캔한다 ─────
-- 기존 UNIQUE는 (신청, 날짜) 순서라 day 단독 범위 검색에 쓸 수 없다. 중복이 아니다.
CALL add_index_if_missing('leave_dates', 'idx_leave_dates_day',
    'CREATE INDEX idx_leave_dates_day ON leave_dates (day)');
CALL add_index_if_missing('schedule_dates', 'idx_schedule_dates_day',
    'CREATE INDEX idx_schedule_dates_day ON schedule_dates (day)');

-- ── 처리 이력 — append-only라 인덱스 비용이 insert에만 붙는다 ────────────────
-- 목록 3종(전체·팀·내가 처리한 것)이 모두 created_at 내림차순 정렬이라
-- 필터 컬럼과 묶어 정렬까지 인덱스로 처리한다.
CALL add_index_if_missing('leave_action_history', 'idx_leave_history_created',
    'CREATE INDEX idx_leave_history_created ON leave_action_history (created_at)');
CALL add_index_if_missing('leave_action_history', 'idx_leave_history_actor_created',
    'CREATE INDEX idx_leave_history_actor_created ON leave_action_history (actor_id, created_at)');
CALL add_index_if_missing('leave_action_history', 'idx_leave_history_user_created',
    'CREATE INDEX idx_leave_history_user_created ON leave_action_history (user_id, created_at)');

CALL add_index_if_missing('welfare_action_history', 'idx_welfare_history_created',
    'CREATE INDEX idx_welfare_history_created ON welfare_action_history (created_at)');
CALL add_index_if_missing('welfare_action_history', 'idx_welfare_history_actor_created',
    'CREATE INDEX idx_welfare_history_actor_created ON welfare_action_history (actor_id, created_at)');
CALL add_index_if_missing('welfare_action_history', 'idx_welfare_history_user_created',
    'CREATE INDEX idx_welfare_history_user_created ON welfare_action_history (user_id, created_at)');

-- ── 기산일 리셋 이력 — 스케줄러가 붙으면서 매년 사원당 1행씩 쌓이기 시작했다 ──
CALL add_index_if_missing('leave_reset_history', 'idx_leave_reset_history_reset_date',
    'CREATE INDEX idx_leave_reset_history_reset_date ON leave_reset_history (reset_date)');

DROP PROCEDURE add_index_if_missing;

-- ── 확인 쿼리 ───────────────────────────────────────────────────────────────
-- SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
--   FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND INDEX_NAME LIKE 'idx_%'
--  GROUP BY TABLE_NAME, INDEX_NAME ORDER BY TABLE_NAME, INDEX_NAME;
