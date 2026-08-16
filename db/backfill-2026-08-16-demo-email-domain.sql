-- ============================================================================
-- 시연 계정 이메일 도메인 교체 + 미발송 메일 취소 (2026-08-16)
--
-- **무슨 일이 있었나**: DemoDataInitializer가 시연 계정 8개를 `@mlsoft.com`으로 만들었다.
-- 시연 계정은 결재·복리후생 이력을 갖고 있어 이메일 알림의 실제 수신자가 되는데,
-- EmailDeliveryService는 `users.email`로 그대로 발송한다. 그 결과 실재하는 회사 도메인 앞으로
-- 메일이 실제로 나갔다(로컬에서 3건 발송 성공 확인).
--
-- 이 스크립트는 **로컬 시연 DB 전용**이다. 운영 DB에서 실행하지 말 것 —
-- 운영의 users.email은 실제 사원의 Google 계정이라 바꾸면 로그인이 끊긴다.
--
-- 멱등하다. 두 번 돌려도 안전하다.
--
-- 실행 전 백업: mysqldump mlsoft_leave users email_history > backup-demo-email.sql
-- ============================================================================

-- ── ① 미발송 메일을 먼저 취소한다 ──────────────────────────────────────────
-- 순서가 중요하다. 주소를 먼저 바꾸면 그사이 재시도 스케줄러(15분 주기)가 새 주소로 발송한다.
-- retry_count를 상한(EmailDeliveryService.MAX_ATTEMPTS = 3)까지 올려 두면
-- findDispatchTargetIds가 더 이상 집지 않는다. 이미 보낸 건(SENT)은 건드리지 않는다.
UPDATE email_history
   SET status = 'FAILED',
       retry_count = 3,
       error_message = '수신 도메인 교체로 발송 취소 (2026-08-16)'
 WHERE status <> 'SENT'
   AND retry_count < 3;

-- ── ② 시연 계정 도메인 교체 ────────────────────────────────────────────────
-- @mlsoft.com을 가진 행만 바꾼다. 실제 로그인 계정(Google 주소)은 도메인이 달라 걸리지 않는다.
UPDATE users
   SET email = REPLACE(email, '@mlsoft.com', '@yedevjun.com')
 WHERE email LIKE '%@mlsoft.com';

-- ── 확인 쿼리 (실행 후 눈으로 볼 것) ────────────────────────────────────────
-- 0이어야 한다:
--   SELECT COUNT(*) FROM users WHERE email LIKE '%@mlsoft.com';
-- 재시도 대기가 남았는지:
--   SELECT status, retry_count, COUNT(*) FROM email_history GROUP BY status, retry_count;
-- 실제로 나간 건(되돌릴 수 없다 — 무엇이 나갔는지 확인용):
--   SELECT h.id, u.email, h.email_type, h.title, h.sent_at
--     FROM email_history h JOIN users u ON u.id = h.user_id
--    WHERE h.status = 'SENT' ORDER BY h.sent_at;
