-- =====================================================================
-- 기존 데이터 backfill (리뷰 O-3 + 1순위 수정 결과)
--
-- 코드를 고쳐도 이미 틀어진 DB 행은 자동으로 낫지 않는다. 스키마 변경과 달리
-- Hibernate가 손대지 않는 영역이라, 배포한 환경마다 1회 실행해야 한다.
--
-- 실행: mysql -u root -p mlsoft_leave < db/backfill-2026-08-07-O3.sql
--
-- 두 문장 모두 멱등하다 — 여러 번 실행해도 결과가 같다.
-- 새로 만든 DB(db/schema.sql로 생성)에는 필요 없다. 적용해도 0행이 바뀐다.
--
-- 적용 이력:
--   2026-08-07 로컬 개발 DB(mlsoft_leave) — department 1행 변경, users 0행 변경
--   홈서버 운영 DB — ⬜ 미적용 (배포 확인 시 실행할 것)
-- =====================================================================

-- ① department.active backfill (O-3)
--
-- 커밋 87be571에서 Department.active가 NOT NULL로 추가됐는데 ddl-auto: update는
-- 컬럼만 만들고 backfill하지 않는다. MySQL이 기존 행을 0으로 채웠다.
-- 그 결과 findByActiveTrueOrderByIdAsc()가 빈 목록을 반환해 부서 드롭다운이 비고,
-- 부서 수정·팀장 지정·부서 배정이 전부 NOT_FOUND가 된다.
-- DataInitializer는 existsByName으로만 판단하므로 재생성도 하지 않는다.
UPDATE department SET active = 1 WHERE active = 0;

-- ② users.advance_days 재계산 (1순위 수정 결과)
--
-- advance_days는 이제 파생값이다 — max(0, use − base − bonus).
-- 코드는 User.syncAdvanceDays()로 통합됐지만, 옛 로직으로 어긋난 채 저장된 행은
-- 그 사원에게 잔액 변경이 한 번 일어나야 재계산이 걸린다. 손대지 않은 행은
-- 다음 기산일까지 틀린 값을 들고 있고, 그때 base_days가 잘못 삭감된다.
-- base_days가 음수인 행(과다 당겨쓰기 정산 이력)도 이 식이 그대로 처리한다.
UPDATE users
SET advance_days = GREATEST(0, use_days - base_days - COALESCE(bonus_days, 0));

-- 검증 — 아래 두 쿼리가 각각 0을 반환해야 한다.
-- SELECT COUNT(*) FROM department WHERE active = 0;
-- SELECT COUNT(*) FROM users
--  WHERE advance_days <> GREATEST(0, use_days - base_days - COALESCE(bonus_days, 0));
