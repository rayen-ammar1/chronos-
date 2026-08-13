"""
Database loader.

Inserts clean records into PostgreSQL in the correct dependency order,
resolving string business keys to auto-generated integer FKs before each insert.

All operations use INSERT ... ON CONFLICT DO NOTHING so the pipeline
is fully idempotent — safe to re-run on the same data without duplicates.

Insertion order (FK dependency chain):
  1. ActivityNature, Client, BillingEntity, Product, Company, Employee
  2. OrganizationalUnit  (self-referencing — parent must exist first)
  3. AccountingCode      (needs ActivityNature, OrganizationalUnit, Product)
  4. MonthPeriod
  5. Project             (needs Client, BillingEntity, Employee)
  6. CompanyMember       (needs Employee, Company)
  7. OrganizationalUnitMember  (needs Employee, OrganizationalUnit)
  8. Lot → Iteration → Phase → Activity  (project hierarchy)
  9. OrganizationalAssignment  (needs Employee, OrganizationalUnit, Product, AccountingCode)
 10. EmployeeTime         (needs Employee, AccountingCode)
"""

import csv
import logging
import os
from datetime import date, datetime
from typing import Optional

from sqlalchemy import create_engine, text
from sqlalchemy.exc import SQLAlchemyError

from config import BATCH_SIZE, ANOMALIES_OUTPUT_DIR
from models.clean_records import (
    AnomalyRecord,
    CleanAccountingCode,
    CleanActivity,
    CleanActivityNature,
    CleanBillingEntity,
    CleanClient,
    CleanCompany,
    CleanCompanyMember,
    CleanEmployee,
    CleanEmployeeTime,
    CleanIteration,
    CleanLot,
    CleanMonthPeriod,
    CleanOrganizationalAssignment,
    CleanOrganizationalUnit,
    CleanOrganizationalUnitMember,
    CleanPhase,
    CleanProduct,
    CleanProject,
)

logger = logging.getLogger(__name__)


class ChronosDbLoader:
    """
    Loads all clean records into PostgreSQL.
    Maintains in-memory ID maps so FK resolution never hits the DB
    more than once per entity type per pipeline run.
    """

    def __init__(self, db_url: str):
        self.engine = create_engine(db_url, pool_pre_ping=True)
        # ID maps: business_key → db_id
        self._employee_ids:    dict[str, int] = {}   # identifier → id
        self._company_ids:     dict[str, int] = {}   # name → id
        self._product_ids:     dict[str, int] = {}   # name → id
        self._client_ids:      dict[str, int] = {}   # name → id
        self._be_ids:          dict[str, int] = {}   # name → id (billing entity)
        self._ou_ids:          dict[str, int] = {}   # name → id
        self._nature_ids:      dict[str, int] = {}   # name → id
        self._acc_code_ids:    dict[str, int] = {}   # operational_identifier → id
        self._project_ids:     dict[str, int] = {}   # name → id
        self._lot_ids:         dict[tuple, int] = {} # (name, project_name) → id
        self._iteration_ids:   dict[tuple, int] = {} # (name, lot_name, project_name) → id
        self._phase_ids:       dict[tuple, int] = {} # (name, iter, lot, proj) → id
        self._month_period_ids: dict[str, int] = {}  # source_label → id

    # ── Public entry point ────────────────────────────────────────────────────

    def load_all(
        self,
        employees:          list[CleanEmployee] = None,
        companies:          list[CleanCompany] = None,
        company_members:    list[CleanCompanyMember] = None,
        products:           list[CleanProduct] = None,
        clients:            list[CleanClient] = None,
        billing_entities:   list[CleanBillingEntity] = None,
        org_units:          list[CleanOrganizationalUnit] = None,
        ou_members:         list[CleanOrganizationalUnitMember] = None,
        activity_natures:   list[CleanActivityNature] = None,
        accounting_codes:   list[CleanAccountingCode] = None,
        projects:           list[CleanProject] = None,
        lots:               list[CleanLot] = None,
        iterations:         list[CleanIteration] = None,
        phases:             list[CleanPhase] = None,
        activities:         list[CleanActivity] = None,
        month_periods:      list[CleanMonthPeriod] = None,
        employee_times:     list[CleanEmployeeTime] = None,
        org_assignments:    list[CleanOrganizationalAssignment] = None,
    ) -> dict[str, int]:
        """
        Load all entities in correct dependency order.
        Returns a summary dict: entity_name → rows_inserted.
        """
        summary = {}

        with self.engine.begin() as conn:
            summary["activity_nature"]    = self._load_activity_natures(conn, activity_natures or [])
            summary["client"]             = self._load_clients(conn, clients or [])
            summary["billing_entity"]     = self._load_billing_entities(conn, billing_entities or [])
            summary["product"]            = self._load_products(conn, products or [])
            summary["company"]            = self._load_companies(conn, companies or [])
            summary["employee"]           = self._load_employees(conn, employees or [])
            summary["organizational_unit"]= self._load_org_units(conn, org_units or [])
            summary["accounting_code"]    = self._load_accounting_codes(conn, accounting_codes or [])
            summary["month_period"]       = self._load_month_periods(conn, month_periods or [])
            summary["project"]            = self._load_projects(conn, projects or [])
            summary["company_member"]     = self._load_company_members(conn, company_members or [])
            summary["ou_member"]          = self._load_ou_members(conn, ou_members or [])
            summary["lot"]                = self._load_lots(conn, lots or [])
            summary["iteration"]          = self._load_iterations(conn, iterations or [])
            summary["phase"]              = self._load_phases(conn, phases or [])
            summary["activity"]           = self._load_activities(conn, activities or [])
            summary["org_assignment"]     = self._load_org_assignments(conn, org_assignments or [])
            summary["employee_time"]      = self._load_employee_times(conn, employee_times or [])

        return summary

    # ── Loaders ───────────────────────────────────────────────────────────────

    def _load_activity_natures(self, conn, records: list[CleanActivityNature]) -> int:
        count = 0
        for r in records:
            result = conn.execute(text("""
                INSERT INTO activity_nature (name)
                VALUES (:name)
                ON CONFLICT (name) DO NOTHING
                RETURNING id
            """), {"name": r.name})
            row = result.fetchone()
            if row:
                self._nature_ids[r.name] = row[0]
                count += 1
        self._nature_ids.update(self._fetch_id_map(conn, "activity_nature", "name"))
        logger.info(f"activity_nature: {count} inserted")
        return count

    def _load_clients(self, conn, records: list[CleanClient]) -> int:
        count = 0
        for r in records:
            result = conn.execute(text("""
                INSERT INTO client (name) VALUES (:name)
                ON CONFLICT DO NOTHING RETURNING id
            """), {"name": r.name})
            row = result.fetchone()
            if row:
                self._client_ids[r.name] = row[0]
                count += 1
        self._client_ids.update(self._fetch_id_map(conn, "client", "name"))
        logger.info(f"client: {count} inserted")
        return count

    def _load_billing_entities(self, conn, records: list[CleanBillingEntity]) -> int:
        count = 0
        for r in records:
            result = conn.execute(text("""
                INSERT INTO billing_entity (name) VALUES (:name)
                ON CONFLICT DO NOTHING RETURNING id
            """), {"name": r.name})
            row = result.fetchone()
            if row:
                self._be_ids[r.name] = row[0]
                count += 1
        self._be_ids.update(self._fetch_id_map(conn, "billing_entity", "name"))
        logger.info(f"billing_entity: {count} inserted")
        return count

    def _load_products(self, conn, records: list[CleanProduct]) -> int:
        count = 0
        for r in records:
            result = conn.execute(text("""
                INSERT INTO product (name) VALUES (:name)
                ON CONFLICT DO NOTHING RETURNING id
            """), {"name": r.name})
            row = result.fetchone()
            if row:
                self._product_ids[r.name] = row[0]
                count += 1
        self._product_ids.update(self._fetch_id_map(conn, "product", "name"))
        logger.info(f"product: {count} inserted")
        return count

    def _load_companies(self, conn, records: list[CleanCompany]) -> int:
        count = 0
        for r in records:
            result = conn.execute(text("""
                INSERT INTO company (name, country)
                VALUES (:name, :country)
                ON CONFLICT DO NOTHING RETURNING id
            """), {"name": r.name, "country": r.country or ""})
            row = result.fetchone()
            if row:
                self._company_ids[r.name] = row[0]
                count += 1
        self._company_ids.update(self._fetch_id_map(conn, "company", "name"))
        logger.info(f"company: {count} inserted")
        return count

    def _load_employees(self, conn, records: list[CleanEmployee]) -> int:
        count = 0
        for r in records:
            if not r.identifier:
                continue
            result = conn.execute(text("""
                INSERT INTO employee (identifier, first_name, last_name)
                VALUES (:identifier, :first_name, :last_name)
                ON CONFLICT (identifier) DO UPDATE
                    SET first_name = EXCLUDED.first_name,
                        last_name  = EXCLUDED.last_name
                WHERE employee.first_name = '' OR employee.first_name IS NULL
                RETURNING id
            """), {
                "identifier": r.identifier,
                "first_name": r.first_name or "",
                "last_name":  r.last_name or "",
            })
            row = result.fetchone()
            if row:
                self._employee_ids[r.identifier] = row[0]
                count += 1
        self._employee_ids.update(
            self._fetch_id_map(conn, "employee", "identifier")
        )
        logger.info(f"employee: {count} inserted/updated")
        return count

    def _load_org_units(self, conn, records: list[CleanOrganizationalUnit]) -> int:
        """
        OrganizationalUnit is self-referencing.
        Insert all records with parent_id = NULL first,
        then do a second pass to set parent_id once all rows exist.
        For this pipeline the source data does not provide parent names —
        parent resolution is a separate manual data setup step.
        """
        count = 0
        for r in records:
            result = conn.execute(text("""
                INSERT INTO organizational_unit (name, parent_id)
                VALUES (:name, NULL)
                ON CONFLICT DO NOTHING RETURNING id
            """), {"name": r.name})
            row = result.fetchone()
            if row:
                self._ou_ids[r.name] = row[0]
                count += 1
        self._ou_ids.update(self._fetch_id_map(conn, "organizational_unit", "name"))
        logger.info(f"organizational_unit: {count} inserted")
        return count

    def _load_accounting_codes(self, conn, records: list[CleanAccountingCode]) -> int:
        count = 0
        for r in records:
            nature_id = self._nature_ids.get(r.activity_nature_name)
            ou_id     = self._ou_ids.get(r.organizational_unit_name) if r.organizational_unit_name else None
            prod_id   = self._product_ids.get(r.product_name) if r.product_name else None

            if not nature_id and r.activity_nature_name:
                logger.warning(
                    f"AccountingCode {r.operational_identifier!r}: "
                    f"activity_nature {r.activity_nature_name!r} not found — skipping."
                )
                continue

            result = conn.execute(text("""
                INSERT INTO accounting_code
                    (operational_identifier, billing_mode, billable,
                     activity_nature_id, organizational_unit_id, product_id)
                VALUES
                    (:op_id, :billing_mode, :billable,
                     :nature_id, :ou_id, :prod_id)
                ON CONFLICT (operational_identifier) DO NOTHING
                RETURNING id
            """), {
                "op_id":        r.operational_identifier,
                "billing_mode": r.billing_mode,
                "billable":     r.billable,
                "nature_id":    nature_id,
                "ou_id":        ou_id,
                "prod_id":      prod_id,
            })
            row = result.fetchone()
            if row:
                self._acc_code_ids[r.operational_identifier] = row[0]
                count += 1
        self._acc_code_ids.update(
            self._fetch_id_map(conn, "accounting_code", "operational_identifier")
        )
        logger.info(f"accounting_code: {count} inserted")
        return count

    def _load_month_periods(self, conn, records: list[CleanMonthPeriod]) -> int:
        count = 0
        for r in records:
            result = conn.execute(text("""
                INSERT INTO month_period (year, month, start_date, end_date, source_label)
                VALUES (:year, :month, :start_date, :end_date, :source_label)
                ON CONFLICT (year, month) DO NOTHING
                RETURNING id
            """), {
                "year":         r.year,
                "month":        r.month,
                "start_date":   r.start_date,
                "end_date":     r.end_date,
                "source_label": r.source_label,
            })
            row = result.fetchone()
            if row:
                self._month_period_ids[r.source_label] = row[0]
                count += 1
        # Refresh map for pre-existing rows
        rows = conn.execute(text(
            "SELECT source_label, id FROM month_period"
        )).fetchall()
        self._month_period_ids.update({row[0]: row[1] for row in rows})
        logger.info(f"month_period: {count} inserted")
        return count

    def _load_projects(self, conn, records: list[CleanProject]) -> int:
        count = 0
        for r in records:
            client_id = self._client_ids.get(r.client_name) if r.client_name else None
            be_id     = self._be_ids.get(r.billing_entity_name) if r.billing_entity_name else None
            mgr_id    = None  # resolved separately after all employees are loaded
            result = conn.execute(text("""
                INSERT INTO project
                    (name, project_manager_id, project_manager_username,
                     status, client_id, billing_entity_id)
                VALUES
                    (:name, :mgr_id, :mgr_username,
                     :status, :client_id, :be_id)
                ON CONFLICT DO NOTHING RETURNING id
            """), {
                "name":         r.name,
                "mgr_id":       mgr_id,
                "mgr_username": r.project_manager_username,
                "status":       r.status,
                "client_id":    client_id,
                "be_id":        be_id,
            })
            row = result.fetchone()
            if row:
                self._project_ids[r.name] = row[0]
                count += 1
        self._project_ids.update(self._fetch_id_map(conn, "project", "name"))
        logger.info(f"project: {count} inserted")
        return count

    def _load_company_members(self, conn, records: list[CleanCompanyMember]) -> int:
        count = 0
        for r in records:
            emp_id = self._employee_ids.get(r.employee_identifier)
            co_id  = self._company_ids.get(r.company_name)
            if not emp_id or not co_id:
                logger.warning(
                    f"CompanyMember: could not resolve employee "
                    f"{r.employee_identifier!r} or company {r.company_name!r} — skipping."
                )
                continue
            conn.execute(text("""
                INSERT INTO company_member
                    (employee_id, company_id, registration_number,
                     start_date, end_date)
                VALUES
                    (:emp_id, :co_id, :reg_num, :start_date, :end_date)
                ON CONFLICT DO NOTHING
            """), {
                "emp_id":    emp_id,
                "co_id":     co_id,
                "reg_num":   r.registration_number,
                "start_date": r.start_date,
                "end_date":  r.end_date,
            })
            count += 1
        logger.info(f"company_member: {count} inserted")
        return count

    def _load_ou_members(self, conn, records: list[CleanOrganizationalUnitMember]) -> int:
        count = 0
        for r in records:
            emp_id = self._employee_ids.get(r.employee_identifier)
            ou_id  = self._ou_ids.get(r.organizational_unit_name)
            if not emp_id or not ou_id:
                continue
            conn.execute(text("""
                INSERT INTO organizational_unit_member
                    (employee_id, organizational_unit_id, start_date, end_date)
                VALUES (:emp_id, :ou_id, :start_date, :end_date)
                ON CONFLICT DO NOTHING
            """), {
                "emp_id":     emp_id,
                "ou_id":      ou_id,
                "start_date": r.start_date,
                "end_date":   r.end_date,
            })
            count += 1
        logger.info(f"organizational_unit_member: {count} inserted")
        return count

    def _load_lots(self, conn, records: list[CleanLot]) -> int:
        count = 0
        for r in records:
            proj_id = self._project_ids.get(r.project_name)
            if not proj_id:
                continue
            result = conn.execute(text("""
                INSERT INTO lot (name, project_id)
                VALUES (:name, :project_id)
                ON CONFLICT DO NOTHING RETURNING id
            """), {"name": r.name, "project_id": proj_id})
            row = result.fetchone()
            if row:
                self._lot_ids[(r.name, r.project_name)] = row[0]
                count += 1
        # Refresh
        rows = conn.execute(text(
            "SELECT l.name, p.name, l.id FROM lot l JOIN project p ON p.id = l.project_id"
        )).fetchall()
        for lot_name, proj_name, lot_id in rows:
            self._lot_ids[(lot_name, proj_name)] = lot_id
        logger.info(f"lot: {count} inserted")
        return count

    def _load_iterations(self, conn, records: list[CleanIteration]) -> int:
        count = 0
        for r in records:
            lot_id = self._lot_ids.get((r.lot_name, r.project_name))
            if not lot_id:
                continue
            result = conn.execute(text("""
                INSERT INTO iteration (name, lot_id)
                VALUES (:name, :lot_id)
                ON CONFLICT DO NOTHING RETURNING id
            """), {"name": r.name, "lot_id": lot_id})
            row = result.fetchone()
            if row:
                self._iteration_ids[(r.name, r.lot_name, r.project_name)] = row[0]
                count += 1
        rows = conn.execute(text("""
            SELECT i.name, l.name, p.name, i.id
            FROM iteration i
            JOIN lot l ON l.id = i.lot_id
            JOIN project p ON p.id = l.project_id
        """)).fetchall()
        for iname, lname, pname, iid in rows:
            self._iteration_ids[(iname, lname, pname)] = iid
        logger.info(f"iteration: {count} inserted")
        return count

    def _load_phases(self, conn, records: list[CleanPhase]) -> int:
        count = 0
        for r in records:
            iter_id = self._iteration_ids.get((r.iteration_name, r.lot_name, r.project_name))
            if not iter_id:
                continue
            result = conn.execute(text("""
                INSERT INTO phase
                    (name, deliverable_name, is_capitalizable,
                     capitalizable_date, is_capitalizable_by, iteration_id)
                VALUES
                    (:name, :deliv, :is_cap, :cap_date, :cap_by, :iter_id)
                ON CONFLICT DO NOTHING RETURNING id
            """), {
                "name":     r.name,
                "deliv":    r.deliverable_name,
                "is_cap":   r.is_capitalizable,
                "cap_date": r.capitalizable_date,
                "cap_by":   r.is_capitalizable_by,
                "iter_id":  iter_id,
            })
            row = result.fetchone()
            if row:
                self._phase_ids[(r.name, r.iteration_name, r.lot_name, r.project_name)] = row[0]
                count += 1
        logger.info(f"phase: {count} inserted")
        return count

    def _load_activities(self, conn, records: list[CleanActivity]) -> int:
        count = 0
        for r in records:
            phase_id = self._phase_ids.get(
                (r.phase_name, r.iteration_name, r.lot_name, r.project_name)
            )
            if not phase_id:
                continue
            conn.execute(text("""
                INSERT INTO activity (name, phase_id)
                VALUES (:name, :phase_id)
                ON CONFLICT DO NOTHING
            """), {"name": r.name, "phase_id": phase_id})
            count += 1
        logger.info(f"activity: {count} inserted")
        return count

    def _load_org_assignments(
        self, conn, records: list[CleanOrganizationalAssignment]
    ) -> int:
        count = 0
        for r in records:
            emp_id  = self._employee_ids.get(r.employee_identifier)
            ou_id   = self._ou_ids.get(r.organizational_unit_name)
            acc_id  = self._acc_code_ids.get(r.accounting_code_identifier)
            prod_id = self._product_ids.get(r.product_name) if r.product_name else None
            if not emp_id or not ou_id or not acc_id:
                logger.warning(
                    f"OrgAssignment: unresolved FK for employee "
                    f"{r.employee_identifier!r} — skipping."
                )
                continue
            conn.execute(text("""
                INSERT INTO organizational_assignment
                    (employee_id, organizational_unit_id, product_id,
                     accounting_code_id, allocation_percentage)
                VALUES
                    (:emp_id, :ou_id, :prod_id, :acc_id, :pct)
                ON CONFLICT DO NOTHING
            """), {
                "emp_id":  emp_id,
                "ou_id":   ou_id,
                "prod_id": prod_id,
                "acc_id":  acc_id,
                "pct":     r.allocation_percentage,
            })
            count += 1
        logger.info(f"organizational_assignment: {count} inserted")
        return count

    def _load_employee_times(self, conn, records: list[CleanEmployeeTime]) -> int:
        """
        Batch insert employee_time rows in chunks of BATCH_SIZE.
        employee_time has no unique constraint beyond its PK — every source
        row is a distinct timesheet entry. Idempotency here requires a
        separate dedup check or a unique index on (employee_id, date, accounting_code_id)
        if the business requires it. For now: ON CONFLICT DO NOTHING as a safety net.
        """
        count = 0
        batch = []

        def flush(b):
            if not b:
                return 0
            conn.execute(text("""
                INSERT INTO employee_time
                    (employee_id, accounting_code_id, date,
                     elapsed_time, man_day, status,
                     validator_username, creator_username, updator_username,
                     comment, price_increase_reason,
                     creation_date, update_date, site)
                VALUES
                    (:emp_id, :acc_id, :date,
                     :elapsed_time, :man_day, :status,
                     :validator, :creator, :updator,
                     :comment, :price_reason,
                     :creation_date, :update_date, :site)
                ON CONFLICT DO NOTHING
            """), b)
            return len(b)

        for r in records:
            emp_id = self._employee_ids.get(r.employee_identifier)
            acc_id = self._acc_code_ids.get(r.accounting_code_identifier)
            if not emp_id or not acc_id:
                logger.warning(
                    f"EmployeeTime: unresolved FK — employee "
                    f"{r.employee_identifier!r} or acc {r.accounting_code_identifier!r}"
                )
                continue
            batch.append({
                "emp_id":        emp_id,
                "acc_id":        acc_id,
                "date":          r.date,
                "elapsed_time":  r.elapsed_time,
                "man_day":       r.man_day,
                "status":        r.status,
                "validator":     r.validator_username,
                "creator":       r.creator_username,
                "updator":       r.updator_username,
                "comment":       r.comment,
                "price_reason":  r.price_increase_reason,
                "creation_date": r.creation_date,
                "update_date":   r.update_date,
                "site":          r.site,
            })
            if len(batch) >= BATCH_SIZE:
                count += flush(batch)
                batch = []
                logger.debug(f"  Flushed batch — {count} rows so far")

        count += flush(batch)
        logger.info(f"employee_time: {count} inserted")
        return count

    # ── Helpers ───────────────────────────────────────────────────────────────

    def _fetch_id_map(self, conn, table: str, key_col: str) -> dict:
        """Load a full name→id map from a simple lookup table."""
        rows = conn.execute(
            text(f"SELECT {key_col}, id FROM {table}")
        ).fetchall()
        return {row[0]: row[1] for row in rows}


# ── Anomaly CSV writer ────────────────────────────────────────────────────────

def write_anomalies_csv(anomalies: list[AnomalyRecord], run_label: str = "") -> str:
    """
    Write all anomaly records to a timestamped CSV file.
    Returns the output file path.
    """
    os.makedirs(ANOMALIES_OUTPUT_DIR, exist_ok=True)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    suffix = f"_{run_label}" if run_label else ""
    filepath = os.path.join(ANOMALIES_OUTPUT_DIR, f"anomalies{suffix}_{ts}.csv")

    fieldnames = [
        "severity", "source_file", "source_row",
        "column", "issue", "raw_value", "context",
    ]

    with open(filepath, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for a in anomalies:
            writer.writerow({
                "severity":    a.severity,
                "source_file": a.source_file,
                "source_row":  a.source_row,
                "column":      a.column,
                "issue":       a.issue,
                "raw_value":   a.raw_value,
                "context":     a.context,
            })

    errors   = sum(1 for a in anomalies if a.severity == "ERROR")
    warnings = sum(1 for a in anomalies if a.severity == "WARNING")
    logger.info(
        f"Anomalies written: {filepath} "
        f"({errors} errors, {warnings} warnings)"
    )
    return filepath
