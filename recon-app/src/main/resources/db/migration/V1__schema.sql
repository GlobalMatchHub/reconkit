-- Reconkit schema.
--
-- Two conventions run through the whole file.
--
-- Money is BIGINT minor units. There is no DECIMAL column in the money path, because a
-- scale set in one place and read back in another is how a fee comparison starts
-- reporting differences that were never there.
--
-- tenant_id is the first column of every unique key and of every index that a query
-- filters on. Hibernate appends the tenant predicate to every statement, and an index
-- that does not lead with it forces the optimiser to filter after the fact, which is
-- exactly wrong on the largest tables in the system.

CREATE TABLE tenant (
    id          VARCHAR(40)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    time_zone   VARCHAR(60)  NOT NULL DEFAULT 'Asia/Seoul',
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     VARCHAR(40)  NOT NULL,
    email         VARCHAR(160) NOT NULL,
    display_name  VARCHAR(80)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE counterparty (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id             VARCHAR(40)  NOT NULL,
    code                  VARCHAR(40)  NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    channel_type          VARCHAR(24)  NOT NULL,
    currency              CHAR(3)      NOT NULL,
    mapping_profile       VARCHAR(60)  NOT NULL,
    active                TINYINT(1)   NOT NULL DEFAULT 1,
    fee_rate_bp           INT          NOT NULL DEFAULT 0,
    fixed_fee_minor       BIGINT       NOT NULL DEFAULT 0,
    fee_vat_bp            INT          NOT NULL DEFAULT 0,
    cycle                 VARCHAR(24)  NOT NULL,
    settle_after_days     INT          NOT NULL DEFAULT 2,
    holdback_bp           INT          NOT NULL DEFAULT 0,
    holdback_release_days INT          NOT NULL DEFAULT 30,
    cutoff_time           TIME         NOT NULL DEFAULT '00:00:00',
    amount_tol_abs        BIGINT       NOT NULL DEFAULT 0,
    amount_tol_bp         INT          NOT NULL DEFAULT 0,
    fee_tol_abs           BIGINT       NOT NULL DEFAULT 1,
    fee_tol_bp            INT          NOT NULL DEFAULT 10,
    match_window_days     INT          NOT NULL DEFAULT 2,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_counterparty_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ingest_batch (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id       VARCHAR(40)  NOT NULL,
    counterparty_id BIGINT       NOT NULL,
    side            VARCHAR(12)  NOT NULL,
    source_name     VARCHAR(200) NOT NULL,
    content_hash    CHAR(64)     NOT NULL,
    business_date   DATE         NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    row_count       INT          NOT NULL DEFAULT 0,
    rejected_count  INT          NOT NULL DEFAULT 0,
    reject_reason   VARCHAR(500) NULL,
    loaded_at       DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- The same file cannot be loaded twice for the same counterparty. Re-sending
    -- yesterday's settlement file is routine, and without this the second load produces
    -- a day of duplicates that look exactly like a counterparty double reporting.
    UNIQUE KEY uk_ingest_hash (tenant_id, counterparty_id, content_hash),
    KEY ix_ingest_date (tenant_id, counterparty_id, business_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ledger_entry (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id       VARCHAR(40) NOT NULL,
    batch_id        BIGINT      NOT NULL,
    counterparty_id BIGINT      NOT NULL,
    external_txn_id VARCHAR(80) NULL,
    approval_no     VARCHAR(60) NULL,
    order_id        VARCHAR(80) NULL,
    merchant_no     VARCHAR(40) NULL,
    occurred_at     DATETIME(6) NOT NULL,
    business_date   DATE        NOT NULL,
    gross_minor     BIGINT      NOT NULL,
    fee_minor       BIGINT      NOT NULL DEFAULT 0,
    net_minor       BIGINT      NOT NULL DEFAULT 0,
    currency        CHAR(3)     NOT NULL,
    txn_status      VARCHAR(20) NOT NULL,
    payment_method  VARCHAR(30) NULL,
    card_bin        VARCHAR(8)  NULL,
    raw_line        TEXT        NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- Pass A is an equality join on this. Leading with tenant and counterparty keeps the
    -- join to one partition of the table per run.
    KEY ix_ledger_txn (tenant_id, counterparty_id, external_txn_id),
    KEY ix_ledger_approval (tenant_id, counterparty_id, approval_no, gross_minor),
    KEY ix_ledger_date (tenant_id, counterparty_id, business_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE statement_entry (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id       VARCHAR(40) NOT NULL,
    batch_id        BIGINT      NOT NULL,
    counterparty_id BIGINT      NOT NULL,
    external_txn_id VARCHAR(80) NULL,
    approval_no     VARCHAR(60) NULL,
    order_id        VARCHAR(80) NULL,
    merchant_no     VARCHAR(40) NULL,
    occurred_at     DATETIME(6) NOT NULL,
    business_date   DATE        NOT NULL,
    gross_minor     BIGINT      NOT NULL,
    fee_minor       BIGINT      NOT NULL DEFAULT 0,
    net_minor       BIGINT      NOT NULL DEFAULT 0,
    currency        CHAR(3)     NOT NULL,
    txn_status      VARCHAR(20) NOT NULL,
    payment_method  VARCHAR(30) NULL,
    card_bin        VARCHAR(8)  NULL,
    raw_line        TEXT        NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_statement_txn (tenant_id, counterparty_id, external_txn_id),
    KEY ix_statement_approval (tenant_id, counterparty_id, approval_no, gross_minor),
    KEY ix_statement_date (tenant_id, counterparty_id, business_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE recon_run (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id             VARCHAR(40)  NOT NULL,
    counterparty_id       BIGINT       NOT NULL,
    business_date         DATE         NOT NULL,
    sequence_no           INT          NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    rule_version          VARCHAR(20)  NOT NULL,
    ledger_count          INT          NOT NULL DEFAULT 0,
    statement_count       INT          NOT NULL DEFAULT 0,
    matched_count         INT          NOT NULL DEFAULT 0,
    discrepancy_count     INT          NOT NULL DEFAULT 0,
    matched_by_pass_a     INT          NOT NULL DEFAULT 0,
    matched_by_pass_b     INT          NOT NULL DEFAULT 0,
    matched_by_pass_c     INT          NOT NULL DEFAULT 0,
    matched_by_pass_d     INT          NOT NULL DEFAULT 0,
    ledger_gross_minor    BIGINT       NOT NULL DEFAULT 0,
    statement_gross_minor BIGINT       NOT NULL DEFAULT 0,
    started_at            DATETIME(6)  NULL,
    finished_at           DATETIME(6)  NULL,
    duration_ms           BIGINT       NULL,
    failure_reason        VARCHAR(500) NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- Two schedulers firing the same night cannot both create a live run. The loser gets
    -- a constraint violation and is handed the winner's run instead of starting a second.
    UNIQUE KEY uk_recon_run_slot (tenant_id, counterparty_id, business_date, sequence_no),
    KEY ix_recon_run_date (tenant_id, business_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE match_group (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id             VARCHAR(40)  NOT NULL,
    run_id                BIGINT       NOT NULL,
    match_type            VARCHAR(24)  NOT NULL,
    matched_by            VARCHAR(24)  NOT NULL,
    confidence            DOUBLE       NOT NULL,
    match_key             VARCHAR(200) NOT NULL,
    ledger_gross_minor    BIGINT       NOT NULL DEFAULT 0,
    statement_gross_minor BIGINT       NOT NULL DEFAULT 0,
    ledger_fee_minor      BIGINT       NOT NULL DEFAULT 0,
    statement_fee_minor   BIGINT       NOT NULL DEFAULT 0,
    currency              CHAR(3)      NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_match_group_run (tenant_id, run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE match_member (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id  VARCHAR(40) NOT NULL,
    group_id   BIGINT      NOT NULL,
    side       VARCHAR(12) NOT NULL,
    entry_id   BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_match_member_group (tenant_id, group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE discrepancy (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id             VARCHAR(40)   NOT NULL,
    run_id                BIGINT        NOT NULL,
    counterparty_id       BIGINT        NOT NULL,
    business_date         DATE          NOT NULL,
    group_id              BIGINT        NULL,
    type                  VARCHAR(32)   NOT NULL,
    severity              VARCHAR(12)   NOT NULL,
    status                VARCHAR(16)   NOT NULL,
    delta_minor           BIGINT        NOT NULL,
    currency              CHAR(3)       NOT NULL,
    ledger_entry_id       BIGINT        NULL,
    statement_entry_id    BIGINT        NULL,
    detail                VARCHAR(500)  NOT NULL,
    assignee              VARCHAR(160)  NULL,
    resolution_note       VARCHAR(1000) NULL,
    resolved_by           VARCHAR(160)  NULL,
    resolved_at           DATETIME(6)   NULL,
    auto_closed_by_run_id BIGINT        NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_discrepancy_queue (tenant_id, status, severity, business_date),
    KEY ix_discrepancy_run (tenant_id, run_id),
    KEY ix_discrepancy_cp (tenant_id, counterparty_id, business_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE settlement (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id              VARCHAR(40)  NOT NULL,
    counterparty_id        BIGINT       NOT NULL,
    statement_no           VARCHAR(40)  NOT NULL,
    period_start           DATE         NOT NULL,
    period_end             DATE         NOT NULL,
    payout_date            DATE         NOT NULL,
    status                 VARCHAR(16)  NOT NULL,
    currency               CHAR(3)      NOT NULL,
    gross_minor            BIGINT       NOT NULL DEFAULT 0,
    refund_minor           BIGINT       NOT NULL DEFAULT 0,
    fee_minor              BIGINT       NOT NULL DEFAULT 0,
    fee_vat_minor          BIGINT       NOT NULL DEFAULT 0,
    adjustment_minor       BIGINT       NOT NULL DEFAULT 0,
    holdback_minor         BIGINT       NOT NULL DEFAULT 0,
    holdback_release_minor BIGINT       NOT NULL DEFAULT 0,
    net_payable_minor      BIGINT       NOT NULL DEFAULT 0,
    txn_count              INT          NOT NULL DEFAULT 0,
    open_discrepancy_count INT          NOT NULL DEFAULT 0,
    confirmed_by           VARCHAR(160) NULL,
    confirmed_at           DATETIME(6)  NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- The last line of defence against paying a period twice. Generation also takes a
    -- row lock, but a constraint holds even when a future caller forgets the lock.
    UNIQUE KEY uk_settlement_period (tenant_id, counterparty_id, period_start, period_end),
    UNIQUE KEY uk_settlement_no (tenant_id, statement_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE settlement_line (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     VARCHAR(40) NOT NULL,
    settlement_id BIGINT      NOT NULL,
    business_date DATE        NOT NULL,
    txn_count     INT         NOT NULL DEFAULT 0,
    gross_minor   BIGINT      NOT NULL DEFAULT 0,
    refund_minor  BIGINT      NOT NULL DEFAULT 0,
    fee_minor     BIGINT      NOT NULL DEFAULT 0,
    fee_vat_minor BIGINT      NOT NULL DEFAULT 0,
    net_minor     BIGINT      NOT NULL DEFAULT 0,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_settlement_line (tenant_id, settlement_id, business_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- A row whose only purpose is to be locked with SELECT ... FOR UPDATE. An in process
-- lock protects one JVM and stops protecting anything the moment a second instance
-- starts, which is exactly when the load that needs it arrives.
CREATE TABLE settlement_lock (
    lock_key    VARCHAR(160) NOT NULL,
    acquired_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (lock_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE reprocess_job (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id        VARCHAR(40)  NOT NULL,
    idempotency_key  VARCHAR(80)  NOT NULL,
    job_type         VARCHAR(40)  NOT NULL,
    target_ref       VARCHAR(120) NOT NULL,
    state            VARCHAR(20)  NOT NULL,
    requested_by     VARCHAR(160) NOT NULL,
    task_total       INT          NOT NULL DEFAULT 0,
    task_succeeded   INT          NOT NULL DEFAULT 0,
    task_failed      INT          NOT NULL DEFAULT 0,
    task_compensated INT          NOT NULL DEFAULT 0,
    started_at       DATETIME(6)  NULL,
    finished_at      DATETIME(6)  NULL,
    failure_reason   VARCHAR(500) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reprocess_job_idem (tenant_id, idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE reprocess_task (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id         VARCHAR(40)   NOT NULL,
    job_id            BIGINT        NOT NULL,
    idempotency_key   VARCHAR(120)  NOT NULL,
    step_no           INT           NOT NULL,
    action            VARCHAR(60)   NOT NULL,
    payload_json      TEXT          NULL,
    compensation_json TEXT          NULL,
    state             VARCHAR(20)   NOT NULL,
    attempt           INT           NOT NULL DEFAULT 0,
    max_attempts      INT           NOT NULL DEFAULT 3,
    last_error        VARCHAR(1000) NULL,
    claimed_at        DATETIME(6)   NULL,
    finished_at       DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reprocess_task_idem (tenant_id, idempotency_key),
    KEY ix_reprocess_task_job (tenant_id, job_id, step_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE audit_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id   VARCHAR(40) NOT NULL,
    actor       VARCHAR(160) NOT NULL,
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(60)  NOT NULL,
    entity_id   VARCHAR(60)  NULL,
    before_json TEXT         NULL,
    after_json  TEXT         NULL,
    request_id  VARCHAR(40)  NOT NULL,
    occurred_at DATETIME(6)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_audit_entity (tenant_id, entity_type, entity_id),
    KEY ix_audit_time (tenant_id, occurred_at),
    KEY ix_audit_request (tenant_id, request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
