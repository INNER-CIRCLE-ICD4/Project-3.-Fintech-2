-- V8: Add performance indexes
-- 성능 최적화를 위한 인덱스 추가

-- 사용자 관련 인덱스
CREATE INDEX IF NOT EXISTS idx_users_phone_number ON users (phone_number);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);

-- 계좌 관련 인덱스
CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts (user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_account_number ON accounts (account_number);
CREATE INDEX IF NOT EXISTS idx_accounts_created_at ON accounts (created_at);

-- 계좌 잔액 관련 인덱스
CREATE INDEX IF NOT EXISTS idx_account_balances_account_number ON account_balances (account_number);
CREATE INDEX IF NOT EXISTS idx_account_balances_updated_at ON account_balances (updated_at);

-- 거래 내역 관련 인덱스
CREATE INDEX IF NOT EXISTS idx_transaction_history_account_number ON transaction_history (account_number);
CREATE INDEX IF NOT EXISTS idx_transaction_history_transaction_type ON transaction_history (transaction_type);
CREATE INDEX IF NOT EXISTS idx_transaction_history_created_at ON transaction_history (created_at);

-- 송금 관련 인덱스
CREATE INDEX IF NOT EXISTS idx_transfers_from_user_id ON transfers (from_user_id);
CREATE INDEX IF NOT EXISTS idx_transfers_to_user_id ON transfers (to_user_id);
CREATE INDEX IF NOT EXISTS idx_transfers_transfer_id ON transfers (transfer_id);
CREATE INDEX IF NOT EXISTS idx_transfers_status ON transfers (status);
CREATE INDEX IF NOT EXISTS idx_transfers_created_at ON transfers (created_at);

-- 결제 관련 인덱스
CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments (user_id);
CREATE INDEX IF NOT EXISTS idx_payments_payment_id ON payments (payment_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (status);
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments (created_at);

-- 감사 로그 관련 인덱스
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type ON audit_logs (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_ip_address ON audit_logs (ip_address);

-- UserAccount 관련 인덱스
CREATE INDEX IF NOT EXISTS idx_user_accounts_user_id ON user_accounts (user_id);
CREATE INDEX IF NOT EXISTS idx_user_accounts_account_number ON user_accounts (account_number);
CREATE INDEX IF NOT EXISTS idx_user_accounts_is_primary ON user_accounts (is_primary);
CREATE INDEX IF NOT EXISTS idx_user_accounts_created_at ON user_accounts (created_at);

-- 로그인 히스토리 관련 인덱스
CREATE INDEX IF NOT EXISTS idx_login_history_user_id ON login_history (user_id);
CREATE INDEX IF NOT EXISTS idx_login_history_created_at ON login_history (created_at);
CREATE INDEX IF NOT EXISTS idx_login_history_ip_address ON login_history (ip_address);

-- 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_transfers_from_user_status_created ON transfers (from_user_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_transfers_to_user_status_created ON transfers (to_user_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_transaction_history_account_type_created ON transaction_history (account_number, transaction_type, created_at);