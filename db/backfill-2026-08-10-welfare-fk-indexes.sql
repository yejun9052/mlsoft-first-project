-- ============================================================================
-- 복리후생 승인자 FK + 조회 인덱스 (2026-08-10, 리뷰 D-5·D-2)
--
-- 두 가지를 한 파일에 담았다. 둘 다 welfare_requests의 승인자 컬럼에 걸리는 변경이고,
-- FK가 만드는 인덱스와 복합 인덱스의 관계를 함께 봐야 하기 때문이다.
--
-- ⚠️ **컬럼은 하나도 추가되지 않는다.** primary_approver_id / sub_approver_id는 원래 있었고
--    (raw BIGINT), 엔티티에서 @ManyToOne 연관으로 바꾼 것뿐이다. 그래서 이 파일을 실행하지
--    않아도 `ddl-auto: validate`는 통과하고 애플리케이션은 기동된다 —
--    Hibernate validate는 **FK 제약도, 인덱스도 검사하지 않는다.**
--    즉 이 파일은 배포를 막지 않지만, 안 돌리면 무결성 보호와 인덱스가 조용히 빠진 상태가 된다.
--
-- 멱등하다 — information_schema로 존재 여부를 보고 건너뛴다.
-- ============================================================================

DELIMITER $$

-- ── 1부: 고아 행 점검 ────────────────────────────────────────────────────────
-- FK를 걸기 전에 참조가 깨진 행이 없는지 본다. 있으면 ALTER가 실패하므로
-- 조용히 넘어가지 않고 명시적으로 멈춘다 — 어떤 행이 문제인지 알아야 고칠 수 있다.
DROP PROCEDURE IF EXISTS check_welfare_approver_orphans$$

CREATE PROCEDURE check_welfare_approver_orphans()
BEGIN
    DECLARE orphan_count INT DEFAULT 0;

    SELECT COUNT(*) INTO orphan_count
      FROM welfare_requests wr
      LEFT JOIN users up ON up.id = wr.primary_approver_id
      LEFT JOIN users us ON us.id = wr.sub_approver_id
     WHERE up.id IS NULL
        OR (wr.sub_approver_id IS NOT NULL AND us.id IS NULL);

    IF orphan_count > 0 THEN
        -- FK가 없던 동안 존재하지 않는 사원 id가 들어갈 수 있었다. 그게 D-5의 요지다.
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'welfare_requests에 존재하지 않는 승인자를 가리키는 행이 있다 — 아래 확인 쿼리로 찾아 고친 뒤 다시 실행할 것';
    END IF;
END$$

-- ── 2부: FK·인덱스 추가 헬퍼 ────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS add_fk_if_missing$$

-- **이름이 아니라 컬럼으로** 존재 여부를 본다.
-- 제약 이름은 환경마다 갈릴 수 있다 — Hibernate가 `ddl-auto: update`로 만든 개발 DB에는
-- 해시 이름(FKn9oej…)이 붙어 있고, 손으로 만든 DB에는 다른 이름이 있을 수 있다.
-- 이름으로 검사하면 그런 DB에서 **같은 컬럼에 FK가 두 개** 생긴다(MySQL이 허용한다).
CREATE PROCEDURE add_fk_if_missing(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN ddl_statement TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND COLUMN_NAME = target_column
          AND REFERENCED_TABLE_NAME IS NOT NULL
    ) THEN
        SET @fk_ddl = ddl_statement;
        PREPARE fk_stmt FROM @fk_ddl;
        EXECUTE fk_stmt;
        DEALLOCATE PREPARE fk_stmt;
    END IF;
END$$

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

CALL check_welfare_approver_orphans();

-- ── 복합 인덱스 먼저 ────────────────────────────────────────────────────────
-- 순서가 중요하다. 이 인덱스들이 승인자 컬럼을 왼쪽 접두로 커버하므로, 먼저 만들면
-- 뒤이어 붙는 FK가 **중복 단일 인덱스를 만들지 않는다**(MySQL은 쓸 수 있는 인덱스가
-- 있으면 FK용 인덱스를 새로 만들지 않는다). 반대 순서면 쓰이지 않는 인덱스가 2개 남는다.
--
-- 목록 3종이 모두 "누구의 + 어떤 상태" 조합이다 — 연차 신청과 같은 근거
-- (db/backfill-2026-08-08-query-indexes.sql 참고). 복리후생만 빠져 있었다.
CALL add_index_if_missing('welfare_requests', 'idx_welfare_requests_user_status',
    'CREATE INDEX idx_welfare_requests_user_status ON welfare_requests (user_id, status)');
CALL add_index_if_missing('welfare_requests', 'idx_welfare_requests_primary_status',
    'CREATE INDEX idx_welfare_requests_primary_status ON welfare_requests (primary_approver_id, status)');
CALL add_index_if_missing('welfare_requests', 'idx_welfare_requests_sub_status',
    'CREATE INDEX idx_welfare_requests_sub_status ON welfare_requests (sub_approver_id, status)');

-- ── 승인자 FK ───────────────────────────────────────────────────────────────
-- 제약 이름은 **Hibernate가 실제로 생성한 것을 그대로 옮겼다** — 임시 DB에 ddl-auto: update로
-- 띄워 SHOW CREATE TABLE로 확인한 값이다. 이렇게 두면 빈 DB에 처음 만든 스키마(db/schema.sql)와
-- 이 파일로 마이그레이션한 DB의 제약 이름이 같아진다.
-- sub_approver_id는 NULL 허용 — 서브 승인자는 선택 사항이고, MySQL FK는 NULL을 검사하지 않는다.
CALL add_fk_if_missing('welfare_requests', 'primary_approver_id',
    'ALTER TABLE welfare_requests ADD CONSTRAINT FKn9oejajygjaj2rmrhg28c18l2 '
    'FOREIGN KEY (primary_approver_id) REFERENCES users (id)');
CALL add_fk_if_missing('welfare_requests', 'sub_approver_id',
    'ALTER TABLE welfare_requests ADD CONSTRAINT FK7yale7ten07ng9mnsa0v2c8w1 '
    'FOREIGN KEY (sub_approver_id) REFERENCES users (id)');

DROP PROCEDURE check_welfare_approver_orphans;
DROP PROCEDURE add_fk_if_missing;
DROP PROCEDURE add_index_if_missing;

-- ── 확인 쿼리 ───────────────────────────────────────────────────────────────
-- 고아 행 찾기 (1부가 멈췄을 때):
-- SELECT wr.id, wr.primary_approver_id, wr.sub_approver_id
--   FROM welfare_requests wr
--   LEFT JOIN users up ON up.id = wr.primary_approver_id
--   LEFT JOIN users us ON us.id = wr.sub_approver_id
--  WHERE up.id IS NULL OR (wr.sub_approver_id IS NOT NULL AND us.id IS NULL);
--
-- 적용 결과:
-- SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
--   FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'welfare_requests'
--  GROUP BY INDEX_NAME;
-- SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'welfare_requests'
--    AND CONSTRAINT_TYPE = 'FOREIGN KEY';
