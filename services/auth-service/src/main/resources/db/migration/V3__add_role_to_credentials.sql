-- 권한(role)을 credentials에 둔다. 인증 주체인 auth가 role을 갖고, JWT 발급 시 claim에 담는다.
-- 기존 계정은 모두 USER로 채운다. ADMIN은 별도로 부여한다(운영/시드 데이터).
ALTER TABLE credentials
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';