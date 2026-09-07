-- ============================================================================
-- 미래 차감 연차 분리 — use_days 재계산 (2026-09-07)
-- 설계: docs/설계-초안/미래-차감-연차-분리-설계-2026-09-07.md §8
--
-- 왜 필요한가
--   이 변경 전에는 신청 시점에 회차를 가리지 않고 전체 날짜를 use_days에서 선차감했다.
--   그래서 "다음 기산일 이후 날짜"가 현재 회차 잔액을 먹은 채로 DB에 남아 있다.
--   새 코드는 현재 회차 창 [last_reset_date, last_reset_date + 1년) 안의 날짜만 use_days에
--   담으므로, 기존 행을 그 정의로 한 번 맞춰 준다.
--
-- 컬럼을 더하지 않는다 — 값 보정 전용이다. 스키마 변경은 없다.
--
-- 멱등하다: 대입이 아니라 재계산이므로 여러 번 돌려도 같은 값으로 수렴한다.
--
-- 실행 전 백업:
--   mysqldump mlsoft_leave users leave_requests leave_dates > backup-before-future-split.sql
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. 실행 전 확인 — 얼마나 바뀔지 먼저 본다 (읽기 전용)
--    diff_days > 0 인 행이 "다음 회차 예약분을 현재 회차에서 빼고 있던" 사원이다.
-- ---------------------------------------------------------------------------
SELECT u.id,
       u.name,
       u.last_reset_date,
       u.use_days                                   AS use_days_before,
       COALESCE(w.window_days, 0)                   AS use_days_after,
       u.use_days - COALESCE(w.window_days, 0)      AS diff_days
  FROM users u
  LEFT JOIN (
        SELECT lr.user_id,
               SUM(CASE lr.leave_type WHEN 'ANNUAL' THEN 1.0 ELSE 0.5 END) AS window_days
          FROM leave_requests lr
          JOIN leave_dates ld ON ld.leave_requests_id = lr.id
          JOIN users lu       ON lu.id = lr.user_id
         WHERE lr.status IN ('APPROVED', 'PENDING', 'CANCEL_PENDING')
           AND lu.last_reset_date IS NOT NULL
           AND ld.day >= lu.last_reset_date
           AND ld.day <  lu.last_reset_date + INTERVAL 1 YEAR
         GROUP BY lr.user_id
       ) w ON w.user_id = u.id
 WHERE u.last_reset_date IS NOT NULL
   AND u.use_days <> COALESCE(w.window_days, 0);

-- ---------------------------------------------------------------------------
-- 1. use_days를 현재 회차 창 안의 살아 있는 날짜 합으로 재계산
--    반열린 구간이다 — 기산일 당일은 포함, 다음 기산일 당일은 제외.
--    last_reset_date가 NULL인 사원(온보딩 미확정)은 건드리지 않는다.
-- ---------------------------------------------------------------------------
UPDATE users u
   SET u.use_days = (
        SELECT COALESCE(SUM(CASE lr.leave_type WHEN 'ANNUAL' THEN 1.0 ELSE 0.5 END), 0)
          FROM leave_requests lr
          JOIN leave_dates ld ON ld.leave_requests_id = lr.id
         WHERE lr.user_id = u.id
           AND lr.status IN ('APPROVED', 'PENDING', 'CANCEL_PENDING')
           AND ld.day >= u.last_reset_date
           AND ld.day <  u.last_reset_date + INTERVAL 1 YEAR
       )
 WHERE u.last_reset_date IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. advance_days 재계산 — 파생값이므로 use_days를 바꾼 뒤 반드시 함께 맞춘다
--    (애플리케이션은 User.syncAdvanceDays 하나로만 이 필드를 쓴다. 같은 식이다)
-- ---------------------------------------------------------------------------
UPDATE users
   SET advance_days = GREATEST(0, use_days - base_days - COALESCE(bonus_days, 0));

-- ---------------------------------------------------------------------------
-- 3. 실행 후 검증 — 아래 두 쿼리가 모두 0행이어야 한다
-- ---------------------------------------------------------------------------

-- 3-1. use_days가 현재 회차 창 합과 다른 사원 (0행이어야 정상)
SELECT u.id, u.name, u.use_days, COALESCE(w.window_days, 0) AS window_days
  FROM users u
  LEFT JOIN (
        SELECT lr.user_id,
               SUM(CASE lr.leave_type WHEN 'ANNUAL' THEN 1.0 ELSE 0.5 END) AS window_days
          FROM leave_requests lr
          JOIN leave_dates ld ON ld.leave_requests_id = lr.id
          JOIN users lu       ON lu.id = lr.user_id
         WHERE lr.status IN ('APPROVED', 'PENDING', 'CANCEL_PENDING')
           AND lu.last_reset_date IS NOT NULL
           AND ld.day >= lu.last_reset_date
           AND ld.day <  lu.last_reset_date + INTERVAL 1 YEAR
         GROUP BY lr.user_id
       ) w ON w.user_id = u.id
 WHERE u.last_reset_date IS NOT NULL
   AND u.use_days <> COALESCE(w.window_days, 0);

-- 3-2. advance_days 불변식이 깨진 사원 (0행이어야 정상)
SELECT id, name, base_days, bonus_days, use_days, advance_days
  FROM users
 WHERE advance_days <> GREATEST(0, use_days - base_days - COALESCE(bonus_days, 0));
