-- ============================================================
-- CHRONOS REPORT PLATFORM — PostgreSQL Schema
-- Version 1.0 — Foundation DDL
-- ============================================================
-- Changes from class diagram:
--   + MonthPeriod entity (was missing, central to report)
--   + ExcludedOrganizationalUnit entity (was missing, spec §3.1.2)
--   ~ Project.projectManager: String → FK employee (data integrity)
--   ~ Project.status: String → ENUM project_status
--   ~ EmployeeTime.status: String → ENUM timesheet_status
--   ~ AccountingCode.billingMode: String → ENUM billing_mode
--   ~ Phase.isCapitalizableBy: String → ENUM capitalizable_by
-- ============================================================


-- ============================================================
-- SECTION 1 — ENUMS
-- ============================================================

CREATE TYPE project_status AS ENUM (
    'ACTIVE',
    'COMPLETED',
    'ON_HOLD',
    'CANCELLED'
);

CREATE TYPE timesheet_status AS ENUM (
    'DRAFT',
    'SUBMITTED',
    'VALIDATED',
    'REJECTED'
);

CREATE TYPE billing_mode AS ENUM (
    'FIXED_PRICE',
    'TIME_AND_MATERIAL',
    'NOT_BILLABLE'
);

CREATE TYPE capitalizable_by AS ENUM (
    'NONE',
    'CLIENT',
    'INTERNAL',
    'BOTH'
);


-- ============================================================
-- SECTION 2 — CORE LOOKUP TABLES
-- ============================================================

CREATE TABLE client (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE billing_entity (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE product (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

-- ActivityNature stores types like HOLIDAYS, PRODUCTIVE, INTERNAL, etc.
-- The value 'HOLIDAYS' is referenced directly in the report logic.
CREATE TABLE activity_nature (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- OrganizationalUnit is self-referencing (parent OU contains child OUs).
-- The report uses parentOrganizationalUnit to build the NO_TS_ prefix.
CREATE TABLE organizational_unit (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    parent_id BIGINT REFERENCES organizational_unit(id) ON DELETE SET NULL
);

CREATE TABLE company (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(255) NOT NULL,
    country VARCHAR(100) NOT NULL
);

-- CountryCalendar drives employee capacity calculation.
-- is_working_day = false covers weekends, public holidays, etc.
CREATE TABLE country_calendar (
    id             BIGSERIAL PRIMARY KEY,
    country        VARCHAR(100) NOT NULL,
    date           DATE         NOT NULL,
    is_working_day BOOLEAN      NOT NULL,
    UNIQUE (country, date)
);

CREATE TABLE employee (
    id         BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    identifier VARCHAR(100) NOT NULL UNIQUE
);


-- ============================================================
-- SECTION 3 — NEW ENTITIES (missing from original class diagram)
-- ============================================================

-- MonthPeriod: the single mandatory input to the report UI.
-- Persisting it lets you audit which periods have been generated.
CREATE TABLE month_period (
    id         BIGSERIAL PRIMARY KEY,
    year       INTEGER NOT NULL,
    month      INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    start_date DATE    NOT NULL,
    end_date   DATE    NOT NULL,
    UNIQUE (year, month)
);

-- ExcludedOrganizationalUnit: OUs whose members skip timesheet obligation.
-- See spec section 3.1.2. One row per excluded OU.
CREATE TABLE excluded_organizational_unit (
    id                      BIGSERIAL PRIMARY KEY,
    organizational_unit_id  BIGINT NOT NULL REFERENCES organizational_unit(id) ON DELETE CASCADE,
    UNIQUE (organizational_unit_id)
);


-- ============================================================
-- SECTION 4 — PROJECT HIERARCHY
-- ============================================================

CREATE TABLE project (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(255)   NOT NULL,
    -- FIXED: was String; now a proper FK to employee
    project_manager_id  BIGINT         REFERENCES employee(id) ON DELETE SET NULL,
    -- FIXED: was String; now a typed enum
    status              project_status NOT NULL DEFAULT 'ACTIVE',
    client_id           BIGINT         REFERENCES client(id),
    billing_entity_id   BIGINT         REFERENCES billing_entity(id)
);

CREATE TABLE lot (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    project_id BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE
);

CREATE TABLE iteration (
    id     BIGSERIAL PRIMARY KEY,
    name   VARCHAR(255) NOT NULL,
    lot_id BIGINT       NOT NULL REFERENCES lot(id) ON DELETE CASCADE
);

CREATE TABLE phase (
    id                  BIGSERIAL       PRIMARY KEY,
    name                VARCHAR(255)    NOT NULL,
    deliverable_name    VARCHAR(255),
    is_capitalizable    BOOLEAN         NOT NULL DEFAULT FALSE,
    capitalizable_date  DATE,
    -- FIXED: was String; now a typed enum
    is_capitalizable_by capitalizable_by NOT NULL DEFAULT 'NONE',
    iteration_id        BIGINT          NOT NULL REFERENCES iteration(id) ON DELETE CASCADE
);

CREATE TABLE activity (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    phase_id BIGINT       NOT NULL REFERENCES phase(id) ON DELETE CASCADE
);


-- ============================================================
-- SECTION 5 — ACCOUNTING CODE
-- ============================================================

-- AccountingCode is the pivot entity for cost allocation.
-- It links an activity nature, OU, and product into a billing code.
-- The operationalIdentifier appears in all report output rows.
CREATE TABLE accounting_code (
    id                     BIGSERIAL    PRIMARY KEY,
    operational_identifier VARCHAR(100) NOT NULL UNIQUE,
    -- FIXED: was String; now a typed enum
    billing_mode           billing_mode NOT NULL,
    billable               BOOLEAN      NOT NULL DEFAULT FALSE,
    activity_id            BIGINT       REFERENCES activity(id),
    activity_nature_id     BIGINT       NOT NULL REFERENCES activity_nature(id),
    organizational_unit_id BIGINT       REFERENCES organizational_unit(id),
    product_id             BIGINT       REFERENCES product(id)
);


-- ============================================================
-- SECTION 6 — EMPLOYEE MEMBERSHIP & ASSIGNMENT TABLES
-- ============================================================

-- CompanyMember: which employee belongs to which company, and when.
-- An employee can have MULTIPLE CompanyMember rows if reassigned.
-- This is the MAIN DRIVER of the report loop.
CREATE TABLE company_member (
    id                  BIGSERIAL PRIMARY KEY,
    employee_id         BIGINT       NOT NULL REFERENCES employee(id),
    company_id          BIGINT       NOT NULL REFERENCES company(id),
    registration_number VARCHAR(100),
    start_date          DATE         NOT NULL,
    end_date            DATE         -- NULL means still active
);

-- EmployeeTime: individual timesheet entries.
-- Grouped by accounting_code_id for the Step 2 report query.
CREATE TABLE employee_time (
    id                    BIGSERIAL PRIMARY KEY,
    date                  DATE               NOT NULL,
    elapsed_time          DOUBLE PRECISION   NOT NULL,  -- days booked
    man_day               DOUBLE PRECISION,
    -- FIXED: was String; now a typed enum
    status                timesheet_status   NOT NULL DEFAULT 'DRAFT',
    validator_id          BIGINT             REFERENCES employee(id),
    comment               TEXT,
    price_increase_reason VARCHAR(255),
    creation_date         DATE,
    creator_user_id       BIGINT,
    update_date           DATE,
    updator_user_id       BIGINT,
    employee_id           BIGINT             NOT NULL REFERENCES employee(id),
    accounting_code_id    BIGINT             NOT NULL REFERENCES accounting_code(id)
);

-- OrganizationalUnitMember: which OU an employee belongs to, during which period.
-- Used to find parentOrganizationalUnit for the NO_TS_ prefix.
CREATE TABLE organizational_unit_member (
    id                      BIGSERIAL PRIMARY KEY,
    employee_id             BIGINT NOT NULL REFERENCES employee(id),
    organizational_unit_id  BIGINT NOT NULL REFERENCES organizational_unit(id),
    start_date              DATE   NOT NULL,
    end_date                DATE
);

-- EmployeeByProduct: which product an employee is assigned to, when.
CREATE TABLE employee_by_product (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employee(id),
    product_id  BIGINT NOT NULL REFERENCES product(id),
    start_date  DATE   NOT NULL,
    end_date    DATE
);

-- EmployeeByActivityNature: which activity nature applies to an employee, when.
CREATE TABLE employee_by_activity_nature (
    id                 BIGSERIAL PRIMARY KEY,
    employee_id        BIGINT NOT NULL REFERENCES employee(id),
    activity_nature_id BIGINT NOT NULL REFERENCES activity_nature(id),
    start_date         DATE   NOT NULL,
    end_date           DATE
);

-- OrganizationalAssignment: default cost allocation (Step 1 of the report).
-- If a record exists for this employee, Step 1 uses it directly and skips Step 2.
CREATE TABLE organizational_assignment (
    id                      BIGSERIAL PRIMARY KEY,
    employee_id             BIGINT           NOT NULL REFERENCES employee(id),
    organizational_unit_id  BIGINT           NOT NULL REFERENCES organizational_unit(id),
    product_id              BIGINT           REFERENCES product(id),
    accounting_code_id      BIGINT           NOT NULL REFERENCES accounting_code(id),
    project_id              BIGINT           REFERENCES project(id),
    allocation_percentage   DOUBLE PRECISION NOT NULL
        CHECK (allocation_percentage > 0 AND allocation_percentage <= 100)
);


-- ============================================================
-- SECTION 7 — INDEXES (CRITICAL FOR REPORT PERFORMANCE)
-- ============================================================

-- The report loops over ALL CompanyMembers in a period.
-- This index is the most important one in the entire schema.
CREATE INDEX idx_cm_employee_dates
    ON company_member (employee_id, start_date, end_date);

CREATE INDEX idx_cm_dates
    ON company_member (start_date, end_date);

-- Inside the loop, timesheets are fetched and grouped per employee + date range.
CREATE INDEX idx_et_employee_date
    ON employee_time (employee_id, date);

CREATE INDEX idx_et_accounting_code
    ON employee_time (accounting_code_id);

-- Step 1 check: does this employee have an OrganizationalAssignment?
CREATE INDEX idx_oa_employee
    ON organizational_assignment (employee_id);

CREATE INDEX idx_oa_project
    ON organizational_assignment (project_id);

-- Fallback lookups (used when no timesheets exist or holidays whole month).
CREATE INDEX idx_oum_employee_dates
    ON organizational_unit_member (employee_id, start_date, end_date);

CREATE INDEX idx_ebp_employee_dates
    ON employee_by_product (employee_id, start_date, end_date);

CREATE INDEX idx_eban_employee_dates
    ON employee_by_activity_nature (employee_id, start_date, end_date);

-- Capacity calculation: count working days in a country/date range.
CREATE INDEX idx_cc_country_date
    ON country_calendar (country, date, is_working_day);


-- ============================================================
-- SECTION 8 — CRITICAL NATIVE QUERIES
-- (Use as @Query(nativeQuery = true) in Spring Data repositories)
-- ============================================================

-- ──────────────────────────────────────────────────────────
-- QUERY 1: Fetch all CompanyMembers active during a MonthPeriod
-- Used in: CompanyMemberRepository.findByMonthPeriod()
-- ──────────────────────────────────────────────────────────
/*
SELECT cm.*
FROM   company_member cm
WHERE  cm.start_date <= :monthEndDate
  AND  (cm.end_date IS NULL OR cm.end_date >= :monthStartDate)
ORDER  BY cm.employee_id, cm.company_id;
*/

-- ──────────────────────────────────────────────────────────
-- KEY INSIGHT: Table 1 (6 cases) collapses to 2 SQL functions
-- ──────────────────────────────────────────────────────────
-- Every row in Table 1 of the spec is a combination of:
--   cm.startDate vs monthStart, and cm.endDate vs monthEnd (or NULL).
-- All 6 cases resolve to the same two expressions:
--
--   search_start = GREATEST(cm.start_date, :monthStartDate)
--   search_end   = LEAST(COALESCE(cm.end_date, :monthEndDate), :monthEndDate)
--
-- Do NOT implement 6 separate if/else branches in Java.
-- Compute these two values once per CompanyMember row and pass them
-- to all subsequent queries (timesheets, OU member, product, etc.).

-- ──────────────────────────────────────────────────────────
-- QUERY 2: Resolve search date range for a CompanyMember
-- Used in: CompanyMemberQueryService.resolveSearchRange()
-- ──────────────────────────────────────────────────────────
/*
SELECT
    GREATEST(cm.start_date,               :monthStartDate) AS search_start,
    LEAST(COALESCE(cm.end_date, :monthEndDate), :monthEndDate) AS search_end
FROM company_member cm
WHERE cm.id = :companyMemberId;
*/

-- ──────────────────────────────────────────────────────────
-- QUERY 3: Fetch timesheets grouped by AccountingCode (Step 2)
-- Used in: EmployeeTimeRepository.sumByAccountingCode()
-- ──────────────────────────────────────────────────────────
/*
SELECT
    et.accounting_code_id,
    SUM(et.elapsed_time) AS days_by_accounting_code
FROM   employee_time et
WHERE  et.employee_id = :employeeId
  AND  et.date BETWEEN :searchStart AND :searchEnd
GROUP  BY et.accounting_code_id;
*/

-- ──────────────────────────────────────────────────────────
-- QUERY 4: Employee capacity = working days from CountryCalendar
-- Used in: EmployeeCapacityService.calculate()
-- ──────────────────────────────────────────────────────────
/*
SELECT COUNT(*)
FROM   country_calendar cc
JOIN   company          co ON co.country = cc.country
JOIN   company_member   cm ON cm.company_id = co.id
WHERE  cm.id            = :companyMemberId
  AND  cc.date BETWEEN  :searchStart AND :searchEnd
  AND  cc.is_working_day = TRUE;
*/

-- ──────────────────────────────────────────────────────────
-- QUERY 5: OrganizationalUnitMember lookup (fallback path)
-- Used in: after detecting no timesheets or holidays-only
-- ──────────────────────────────────────────────────────────
/*
SELECT oum.*, ou.parent_id
FROM   organizational_unit_member oum
JOIN   organizational_unit ou ON ou.id = oum.organizational_unit_id
WHERE  oum.employee_id = :employeeId
  AND  oum.start_date <= :searchEnd
  AND  (oum.end_date IS NULL OR oum.end_date >= :searchStart)
LIMIT  1;
*/
