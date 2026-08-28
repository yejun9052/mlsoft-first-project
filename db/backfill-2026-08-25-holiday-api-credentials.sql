-- 공휴일 API 자격 증명 저장소 (2026-08-25)
--
-- 원문 API 키는 이 파일에 넣지 않는다.
-- HOLIDAY_API_KEY + HOLIDAY_CREDENTIAL_ENCRYPTION_KEY + HOLIDAY_CREDENTIAL_SEED=true를
-- 주입한 상태로 앱을 최초 1회 기동하면 HolidayApiCredentialInitializer가 AES-GCM
-- 암호문을 저장한다. 저장 후에는 HOLIDAY_CREDENTIAL_SEED를 false 또는 미설정으로 둔다.
-- HOLIDAY_CREDENTIAL_ENCRYPTION_KEY는 DB와 별도 보관해야 하며, 두 값을 모두
-- 잃으면 저장된 키를 복호화할 수 없다.
CREATE TABLE IF NOT EXISTS `holiday_api_credentials` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `encrypted_api_key` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `active` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_holiday_api_credentials_provider` (`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
