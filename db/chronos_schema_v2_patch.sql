-- ============================================================
-- CHRONOS SCHEMA — v2 PATCH (applied after data analysis)
-- Run these against the v1 schema to apply corrections
-- ============================================================

-- FIX 1: billing_mode enum — add NOTAPPLICABLE found in source data
-- Also rename NOT_BILLABLE for consistency; add REGIE (French T&M)
ALTER TYPE billing_mode RENAME TO billing_mode_old;
CREATE TYPE billing_mode AS ENUM (
    'FIXED_PRICE',       -- Forfait in French source
    'TIME_AND_MATERIAL', -- Régie in French source
    'NOTAPPLICABLE',     -- Present in Employee Time source data
    'NOT_BILLABLE'       -- Keep for internal activities
);
ALTER TABLE accounting_code
    ALTER COLUMN billing_mode TYPE billing_mode
    USING billing_mode::text::billing_mode;
DROP TYPE billing_mode_old;

-- FIX 2: is_capitalizable_by — source data holds usernames, not enum values
-- Dropping the enum and using VARCHAR until business rules are clarified
ALTER TABLE phase
    ALTER COLUMN is_capitalizable_by TYPE VARCHAR(100)
    USING is_capitalizable_by::text;
DROP TYPE IF EXISTS capitalizable_by;
-- TODO: Once the business confirms meaning, replace with proper FK or enum

-- FIX 3: validator_id, creator_user_id, updator_user_id — are string usernames
-- Change from BIGINT to VARCHAR(50) to match source data format
ALTER TABLE employee_time
    DROP COLUMN validator_id,
    DROP COLUMN creator_user_id,
    DROP COLUMN updator_user_id;

ALTER TABLE employee_time
    ADD COLUMN validator_username    VARCHAR(50),   -- e.g. 'rogmorgan'
    ADD COLUMN creator_username      VARCHAR(50),   -- e.g. 'karmathew'
    ADD COLUMN updator_username      VARCHAR(50);   -- e.g. 'cryespino'

-- FIX 4: project_manager is also a string username in source data
-- Add a username field alongside the FK for import traceability
ALTER TABLE project
    ADD COLUMN project_manager_username VARCHAR(50); -- e.g. 'steanders'
-- Note: project_manager_id (FK to employee) stays for resolved references
-- Python import: resolve username → employee.id, populate both fields

-- FIX 5: project_status — NULL in source data, make optional
ALTER TABLE project
    ALTER COLUMN status DROP NOT NULL,
    ALTER COLUMN status DROP DEFAULT;

-- FIX 6: Add site field — present in every Employee Time row, not in schema
ALTER TABLE employee_time
    ADD COLUMN site VARCHAR(100); -- e.g. 'ATVERME G'

-- FIX 7: Add project_id to organizational_assignment for Product Manager dashboard
ALTER TABLE organizational_assignment
    ADD COLUMN project_id BIGINT REFERENCES project(id);
CREATE INDEX idx_oa_project ON organizational_assignment (project_id);

-- FIX 7: man_day is the reporting metric — add a NOT NULL constraint note
-- elapsed_time = hours booked (source unit)
-- man_day      = business days (8 hours = 1 day, used in ALL report calculations)
-- No DDL change but document in column comments:
COMMENT ON COLUMN employee_time.elapsed_time IS
    'Hours booked (source unit from CSV). 1 man_day = 8 elapsed_time units. Do NOT use for report calculations.';
COMMENT ON COLUMN employee_time.man_day IS
    'Business days booked (reporting unit). Always use this for ratio calculations and capacity comparisons.';

-- FIX 8: Month Period source format is month|year (e.g. "1|2024")
-- Add a source_label field to month_period for traceability
ALTER TABLE month_period
    ADD COLUMN source_label VARCHAR(20); -- stores original "1|2024" format

-- ============================================================
-- SEED DATA: activity_nature values observed in source data
-- ============================================================
INSERT INTO activity_nature (name) VALUES
    ('SUPPORT'),
    ('REGIE'),
    ('FORFAIT'),
    ('RPS'),
    ('MAINTENANCE'),
    ('HOLIDAYS')    -- required by report logic for holidays detection
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- OPEN QUESTIONS — block deployment until answered
-- ============================================================
-- Q1: Split allocations — do multiple org_assignment rows per employee
--     sum to 100%? Or can partial allocation (<100%) be valid?
--     Impact: affects Step 1 → Step 2 decision logic in report.

-- Q2: Two AccountingCode formats (hash vs readable) — same table or
--     separate source systems? Is there a mapping key between them?
--     Impact: affects how accounting_code is populated at import.

-- Q3: Is Capitalizable By — usernames or enum? What does this field
--     actually represent in the business context?
--     Impact: schema stays VARCHAR(100) until clarified.
