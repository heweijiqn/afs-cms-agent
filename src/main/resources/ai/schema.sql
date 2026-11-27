-- AI platform layer: retrieval, prompt registry, semantic cache, async jobs, eval.
-- All tables are additive and idempotent so the local workspace upgrades in place.

CREATE TABLE IF NOT EXISTS ai_rag_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_type VARCHAR(32) NOT NULL,
    doc_id VARCHAR(128) NOT NULL,
    site VARCHAR(128),
    title VARCHAR(512),
    chunk_index INT NOT NULL,
    content CLOB NOT NULL,
    tokens INT NOT NULL DEFAULT 0,
    embedding VARCHAR(8192) NOT NULL,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ai_rag_chunk_doc ON ai_rag_chunk(doc_id);
CREATE INDEX IF NOT EXISTS ai_rag_chunk_site ON ai_rag_chunk(site);

CREATE TABLE IF NOT EXISTS ai_prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt_key VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content CLOB NOT NULL,
    variables VARCHAR(512),
    active INT NOT NULL DEFAULT 0,
    note VARCHAR(256),
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (prompt_key, version)
);

CREATE TABLE IF NOT EXISTS ai_semantic_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model VARCHAR(128) NOT NULL,
    prompt_hash VARCHAR(64) NOT NULL,
    prompt CLOB NOT NULL,
    embedding VARCHAR(8192) NOT NULL,
    answer CLOB NOT NULL,
    hits INT NOT NULL DEFAULT 0,
    tokens_saved INT NOT NULL DEFAULT 0,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_hit_at TIMESTAMP,
    UNIQUE (model, prompt_hash)
);

CREATE TABLE IF NOT EXISTS ai_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    payload CLOB,
    idempotency_key VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    run_after TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    locked_by VARCHAR(64),
    last_error VARCHAR(1024),
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (idempotency_key)
);
CREATE INDEX IF NOT EXISTS ai_job_status ON ai_job(status, run_after);

CREATE TABLE IF NOT EXISTS ai_eval_suite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(512),
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_eval_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    question VARCHAR(1024) NOT NULL,
    expected_keywords VARCHAR(512),
    site VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS ai_eval_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    suite_id BIGINT NOT NULL,
    metrics CLOB,
    cases INT NOT NULL,
    passed INT NOT NULL,
    avg_latency_ms INT NOT NULL,
    cache_hit_rate DOUBLE DEFAULT 0,
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
