-- FreightFox Transporter Assignment Platform - Initial Schema
-- V1__init_schema.sql

-- 1. Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- 2. Lanes table
CREATE TABLE IF NOT EXISTS lanes (
    id BIGSERIAL PRIMARY KEY,
    external_lane_id VARCHAR(64) NOT NULL,
    origin VARCHAR(128) NOT NULL,
    destination VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lanes_external_id UNIQUE (external_lane_id)
);

-- 3. Transporters table
CREATE TABLE IF NOT EXISTS transporters (
    id BIGSERIAL PRIMARY KEY,
    external_transporter_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transporters_external_id UNIQUE (external_transporter_id)
);

-- 4. Transporter Quotes table
CREATE TABLE IF NOT EXISTS transporter_quotes (
    id BIGSERIAL PRIMARY KEY,
    lane_id BIGINT NOT NULL,
    transporter_id BIGINT NOT NULL,
    quote_amount NUMERIC(15, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quotes_lane FOREIGN KEY (lane_id) REFERENCES lanes (id) ON DELETE CASCADE,
    CONSTRAINT fk_quotes_transporter FOREIGN KEY (transporter_id) REFERENCES transporters (id) ON DELETE CASCADE,
    CONSTRAINT uk_quote_lane_transporter UNIQUE (lane_id, transporter_id),
    CONSTRAINT chk_quote_positive CHECK (quote_amount > 0)
);

-- 5. Assignment Runs table
CREATE TABLE IF NOT EXISTS assignment_runs (
    id BIGSERIAL PRIMARY KEY,
    requested_max_transporters INT NOT NULL,
    selected_transporter_count INT NOT NULL,
    total_cost NUMERIC(15, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    optimization_objective VARCHAR(64) NOT NULL,
    lanes_covered INT NOT NULL DEFAULT 0,
    created_by VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_max_transporters_positive CHECK (requested_max_transporters >= 1),
    CONSTRAINT chk_selected_transporters_nonneg CHECK (selected_transporter_count >= 0),
    CONSTRAINT chk_total_cost_nonneg CHECK (total_cost >= 0)
);

-- 6. Lane Assignments table
CREATE TABLE IF NOT EXISTS lane_assignments (
    id BIGSERIAL PRIMARY KEY,
    assignment_run_id BIGINT NOT NULL,
    lane_id BIGINT NOT NULL,
    transporter_id BIGINT NOT NULL,
    quote_amount NUMERIC(15, 2) NOT NULL,
    CONSTRAINT fk_assignment_run FOREIGN KEY (assignment_run_id) REFERENCES assignment_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_lane FOREIGN KEY (lane_id) REFERENCES lanes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_transporter FOREIGN KEY (transporter_id) REFERENCES transporters (id) ON DELETE RESTRICT,
    CONSTRAINT chk_assignment_quote_positive CHECK (quote_amount > 0)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_lanes_external_id ON lanes (external_lane_id);
CREATE INDEX IF NOT EXISTS idx_transporters_external_id ON transporters (external_transporter_id);
CREATE INDEX IF NOT EXISTS idx_quotes_lane_id ON transporter_quotes (lane_id);
CREATE INDEX IF NOT EXISTS idx_quotes_transporter_id ON transporter_quotes (transporter_id);
CREATE INDEX IF NOT EXISTS idx_lane_assignments_run_id ON lane_assignments (assignment_run_id);
CREATE INDEX IF NOT EXISTS idx_assignment_runs_created_at ON assignment_runs (created_at DESC);
