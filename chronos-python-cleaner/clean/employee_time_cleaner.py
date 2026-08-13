"""
Employee Time CSV cleaning pipeline.

Reads the main source file (Book1.xlsx / employee_time.csv — 43 columns)
and produces a set of clean typed records for every entity it touches:
  Employee, Company, CompanyMember, OrganizationalUnit,
  OrganizationalUnitMember, ActivityNature, AccountingCode,
  Client, BillingEntity, Product, Project, Lot, Iteration,
  Phase, Activity, MonthPeriod, EmployeeTime.

Every row either contributes to a clean record or generates an AnomalyRecord.
Nothing in between — no silent coercions.
"""

import logging
from dataclasses import dataclass, field
from typing import Optional

import pandas as pd

from clean.date_utils import normalize_date, normalize_datetime, parse_month_period
from clean.validators import (
    normalize_billing_mode,
    normalize_bool,
    normalize_project_status,
    normalize_string,
    normalize_timesheet_status,
    normalize_product_name,
    check_company_member_dates,
    check_man_day_present,
    check_man_day_ratio,
    check_required_string,
)
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
    CleanOrganizationalUnit,
    CleanOrganizationalUnitMember,
    CleanPhase,
    CleanProduct,
    CleanProject,
)

logger = logging.getLogger(__name__)

# ── Column name map ───────────────────────────────────────────────────────────
# Maps source CSV headers (as they appear in the file) to clean internal names.
# This is the single place to fix if the source changes a column name.

COLUMN_MAP = {
    "Organizational Unit Name":       "ou_name",
    "Product Name":                   "product_name",
    "Client Name":                    "client_name",
    "Billing Entity Name":            "billing_entity_name",
    "Project Name":                   "project_name",
    "Project Manager":                "project_manager_username",
    "Lot Name":                       "lot_name",
    "Iteration Name":                 "iteration_name",
    "Phase Name":                     "phase_name",
    "Project Status":                 "project_status",
    "Activity Name":                  "activity_name",
    "Employee Identifier":            "employee_identifier",
    "Employee FirstName":             "employee_first_name",
    "Employee lastName":              "employee_last_name",
    "Employee Company Name":          "company_name",
    "Employee Company Start Date":    "company_start_date",
    "Employee Company End Date":      "company_end_date",
    "Employee Registration Number":   "registration_number",
    "Employee Org Unit Name":         "emp_ou_name",
    "Employee Org Unit Start Date":   "emp_ou_start_date",
    "Employee Org Unit End Date":     "emp_ou_end_date",
    "ACC Operational Identifier":     "acc_identifier",
    "ACC Activity Nature":            "acc_activity_nature",
    "ACC Billing Mode":               "acc_billing_mode",
    "Billable":                       "billable",
    "Day":                            "day",
    "Elapsed Time":                   "elapsed_time",
    "Man Day":                        "man_day",
    "Site":                           "site",
    "Price Increase Reason":          "price_increase_reason",
    "Comment":                        "comment",
    "Creation Date":                  "creation_date",
    "Creator User Id":                "creator_username",
    "Update Date":                    "update_date",
    "Updator User Id":                "updator_username",
    "Employee Time Status":           "et_status",
    "Validator Id":                   "validator_username",
    "Delivrable Name":                "deliverable_name",   # typo in source is intentional
    "is Capitalizable":               "is_capitalizable",
    "Capitalizable Date":             "capitalizable_date",
    "Is Capitalizable By":            "is_capitalizable_by",
    "Month Period":                   "month_period",
}


@dataclass
class EmployeeTimeCleanResult:
    """Everything extracted from one run of the employee time pipeline."""
    employees:              list[CleanEmployee]              = field(default_factory=list)
    companies:              list[CleanCompany]               = field(default_factory=list)
    company_members:        list[CleanCompanyMember]         = field(default_factory=list)
    products:               list[CleanProduct]               = field(default_factory=list)
    clients:                list[CleanClient]                = field(default_factory=list)
    billing_entities:       list[CleanBillingEntity]         = field(default_factory=list)
    organizational_units:   list[CleanOrganizationalUnit]    = field(default_factory=list)
    ou_members:             list[CleanOrganizationalUnitMember] = field(default_factory=list)
    activity_natures:       list[CleanActivityNature]        = field(default_factory=list)
    accounting_codes:       list[CleanAccountingCode]        = field(default_factory=list)
    projects:               list[CleanProject]               = field(default_factory=list)
    lots:                   list[CleanLot]                   = field(default_factory=list)
    iterations:             list[CleanIteration]             = field(default_factory=list)
    phases:                 list[CleanPhase]                 = field(default_factory=list)
    activities:             list[CleanActivity]              = field(default_factory=list)
    month_periods:          list[CleanMonthPeriod]           = field(default_factory=list)
    employee_times:         list[CleanEmployeeTime]          = field(default_factory=list)
    anomalies:              list[AnomalyRecord]              = field(default_factory=list)


def clean_employee_time_file(
    filepath: str,
    sheet_name: int = 0,
) -> EmployeeTimeCleanResult:
    """
    Main entry point. Reads the source file, cleans every row,
    deduplicates lookup entities, and returns a EmployeeTimeCleanResult.
    """
    logger.info(f"Reading employee time file: {filepath}")
    df = pd.read_excel(filepath, sheet_name=sheet_name, dtype=str)

    # Rename columns using the map; ignore any unmapped columns
    df = df.rename(columns=COLUMN_MAP)
    logger.info(f"Loaded {len(df)} rows, {len(df.columns)} columns")

    result = EmployeeTimeCleanResult()

    # Dedup sets (keyed by business identifier to avoid duplicate clean records)
    seen_employees:    set[str]          = set()
    seen_companies:    set[str]          = set()
    seen_cm:           set[tuple]        = set()
    seen_products:     set[str]          = set()
    seen_clients:      set[str]          = set()
    seen_be:           set[str]          = set()
    seen_ous:          set[str]          = set()
    seen_ou_members:   set[tuple]        = set()
    seen_natures:      set[str]          = set()
    seen_acc_codes:    set[str]          = set()
    seen_projects:     set[str]          = set()
    seen_lots:         set[tuple]        = set()
    seen_iterations:   set[tuple]        = set()
    seen_phases:       set[tuple]        = set()
    seen_activities:   set[tuple]        = set()
    seen_periods:      set[str]          = set()

    for idx, raw in df.iterrows():
        row_num = int(idx) + 2  # +2 for 1-based + header row
        src = filepath

        def anomaly(column, issue, severity="ERROR", raw_value=""):
            result.anomalies.append(AnomalyRecord(
                source_file=src,
                source_row=row_num,
                column=column,
                issue=issue,
                severity=severity,
                raw_value=str(raw_value),
            ))

        # ── Parse and validate all fields first ───────────────────────────

        try:
            # Required string fields
            emp_id   = normalize_string(raw.get("employee_identifier"))
            acc_id   = normalize_string(raw.get("acc_identifier"))
            emp_fn   = normalize_string(raw.get("employee_first_name"))
            emp_ln   = normalize_string(raw.get("employee_last_name"))
            co_name  = normalize_string(raw.get("company_name"))
            ou_name  = normalize_string(raw.get("ou_name"))
            emp_ou   = normalize_string(raw.get("emp_ou_name"))

            for field_val, field_name in [
                (emp_id,  "Employee Identifier"),
                (acc_id,  "ACC Operational Identifier"),
                (emp_fn,  "Employee FirstName"),
                (emp_ln,  "Employee lastName"),
                (co_name, "Employee Company Name"),
            ]:
                err = check_required_string(field_val, field_name, src, row_num)
                if err:
                    result.anomalies.append(err)
                    # can't process this row without the key fields
                    if field_name in ("Employee Identifier", "ACC Operational Identifier"):
                        raise _SkipRow(f"Missing key field: {field_name}")

            # Dates — these use the mixed-type normalizer
            company_start = normalize_date(raw.get("company_start_date"))
            company_end   = normalize_date(raw.get("company_end_date"))
            emp_ou_start  = normalize_date(raw.get("emp_ou_start_date"))
            emp_ou_end    = normalize_date(raw.get("emp_ou_end_date"))
            et_date       = normalize_date(raw.get("day"))
            cap_date      = normalize_date(raw.get("capitalizable_date"))
            creation_dt   = normalize_datetime(raw.get("creation_date"))
            update_dt     = normalize_datetime(raw.get("update_date"))

            # Company member date anomaly check
            cm_date_err = check_company_member_dates(
                company_start, company_end, src, row_num
            )
            if cm_date_err:
                result.anomalies.append(cm_date_err)
                # Don't skip — still extract employee, company, etc.
                # Just don't create the CompanyMember record.
                company_end_clean = None
                company_member_ok = False
            else:
                company_end_clean = company_end
                company_member_ok = True

            # Numeric fields
            elapsed_time = float(raw.get("elapsed_time", 0) or 0)
            man_day_raw  = raw.get("man_day")
            man_day_err  = check_man_day_present(man_day_raw, src, row_num)
            if man_day_err:
                result.anomalies.append(man_day_err)
                raise _SkipRow("man_day is missing or zero")
            man_day = float(man_day_raw)

            ratio_warn = check_man_day_ratio(elapsed_time, man_day, src, row_num)
            if ratio_warn:
                result.anomalies.append(ratio_warn)

            # Enum normalization
            et_status    = normalize_timesheet_status(raw.get("et_status"))
            billing_mode = normalize_billing_mode(raw.get("acc_billing_mode"))
            proj_status  = normalize_project_status(raw.get("project_status"))
            billable     = normalize_bool(raw.get("billable"))
            is_cap       = normalize_bool(raw.get("is_capitalizable"))

            # Optional strings
            product_name  = normalize_product_name(raw.get("product_name"))
            client_name   = normalize_string(raw.get("client_name"))
            be_name       = normalize_string(raw.get("billing_entity_name"))
            proj_name     = normalize_string(raw.get("project_name"))
            proj_mgr      = normalize_string(raw.get("project_manager_username"), max_length=50)
            lot_name      = normalize_string(raw.get("lot_name"))
            iter_name     = normalize_string(raw.get("iteration_name"))
            phase_name    = normalize_string(raw.get("phase_name"))
            act_name      = normalize_string(raw.get("activity_name"))
            nature_name   = normalize_string(raw.get("acc_activity_nature"))
            site          = normalize_string(raw.get("site"), max_length=100)
            comment       = normalize_string(raw.get("comment"))
            price_reason  = normalize_string(raw.get("price_increase_reason"))
            validator     = normalize_string(raw.get("validator_username"), max_length=50)
            creator       = normalize_string(raw.get("creator_username"), max_length=50)
            updator       = normalize_string(raw.get("updator_username"), max_length=50)
            deliv_name    = normalize_string(raw.get("deliverable_name"))
            is_cap_by     = normalize_string(raw.get("is_capitalizable_by"), max_length=100)
            reg_num       = normalize_string(raw.get("registration_number"), max_length=100)

            # Month period
            mp_raw = raw.get("month_period")
            mp_month, mp_year, mp_start, mp_end, mp_label = parse_month_period(mp_raw)

        except _SkipRow as e:
            logger.debug(f"Row {row_num} skipped: {e}")
            continue
        except Exception as e:
            anomaly("(multiple)", f"Unexpected parse error: {e}", "ERROR", "")
            continue

        # ── Accumulate clean records (deduplication by business key) ──────

        if emp_id not in seen_employees:
            seen_employees.add(emp_id)
            result.employees.append(CleanEmployee(
                identifier=emp_id,
                first_name=emp_fn,
                last_name=emp_ln,
            ))

        if co_name and co_name not in seen_companies:
            seen_companies.add(co_name)
            result.companies.append(CleanCompany(name=co_name, country=""))

        cm_key = (emp_id, co_name, str(company_start))
        if company_member_ok and cm_key not in seen_cm:
            seen_cm.add(cm_key)
            result.company_members.append(CleanCompanyMember(
                employee_identifier=emp_id,
                company_name=co_name,
                registration_number=reg_num,
                start_date=company_start,
                end_date=company_end_clean,
            ))

        if product_name and product_name not in seen_products:
            seen_products.add(product_name)
            result.products.append(CleanProduct(name=product_name))

        if client_name and client_name not in seen_clients:
            seen_clients.add(client_name)
            result.clients.append(CleanClient(name=client_name))

        if be_name and be_name not in seen_be:
            seen_be.add(be_name)
            result.billing_entities.append(CleanBillingEntity(name=be_name))

        if ou_name and ou_name not in seen_ous:
            seen_ous.add(ou_name)
            result.organizational_units.append(CleanOrganizationalUnit(name=ou_name, parent_name=None))

        if emp_ou and emp_ou not in seen_ous:
            seen_ous.add(emp_ou)
            result.organizational_units.append(CleanOrganizationalUnit(name=emp_ou, parent_name=None))

        ou_member_key = (emp_id, emp_ou, str(emp_ou_start))
        if emp_ou and ou_member_key not in seen_ou_members:
            seen_ou_members.add(ou_member_key)
            result.ou_members.append(CleanOrganizationalUnitMember(
                employee_identifier=emp_id,
                organizational_unit_name=emp_ou,
                start_date=emp_ou_start,
                end_date=emp_ou_end,
            ))

        if nature_name and nature_name not in seen_natures:
            seen_natures.add(nature_name)
            result.activity_natures.append(CleanActivityNature(name=nature_name))

        if acc_id and acc_id not in seen_acc_codes:
            seen_acc_codes.add(acc_id)
            result.accounting_codes.append(CleanAccountingCode(
                operational_identifier=acc_id,
                billing_mode=billing_mode,
                billable=billable,
                activity_nature_name=nature_name or "",
                organizational_unit_name=ou_name,
                product_name=product_name,
            ))

        if proj_name and proj_name not in seen_projects:
            seen_projects.add(proj_name)
            result.projects.append(CleanProject(
                name=proj_name,
                project_manager_username=proj_mgr,
                status=proj_status,
                client_name=client_name,
                billing_entity_name=be_name,
            ))

        lot_key = (lot_name, proj_name)
        if lot_name and lot_key not in seen_lots:
            seen_lots.add(lot_key)
            result.lots.append(CleanLot(name=lot_name, project_name=proj_name))

        iter_key = (iter_name, lot_name, proj_name)
        if iter_name and iter_key not in seen_iterations:
            seen_iterations.add(iter_key)
            result.iterations.append(CleanIteration(
                name=iter_name, lot_name=lot_name, project_name=proj_name
            ))

        phase_key = (phase_name, iter_name, lot_name, proj_name)
        if phase_name and phase_key not in seen_phases:
            seen_phases.add(phase_key)
            result.phases.append(CleanPhase(
                name=phase_name,
                deliverable_name=deliv_name,
                is_capitalizable=is_cap,
                capitalizable_date=cap_date,
                is_capitalizable_by=is_cap_by,
                iteration_name=iter_name,
                lot_name=lot_name,
                project_name=proj_name,
            ))

        act_key = (act_name, phase_name, iter_name, lot_name, proj_name)
        if act_name and act_key not in seen_activities:
            seen_activities.add(act_key)
            result.activities.append(CleanActivity(
                name=act_name,
                phase_name=phase_name,
                iteration_name=iter_name,
                lot_name=lot_name,
                project_name=proj_name,
            ))

        if mp_label not in seen_periods:
            seen_periods.add(mp_label)
            result.month_periods.append(CleanMonthPeriod(
                year=mp_year,
                month=mp_month,
                start_date=mp_start,
                end_date=mp_end,
                source_label=mp_label,
            ))

        # EmployeeTime — one record per source row (no dedup needed)
        if et_date:
            result.employee_times.append(CleanEmployeeTime(
                employee_identifier=emp_id,
                accounting_code_identifier=acc_id,
                date=et_date,
                elapsed_time=elapsed_time,
                man_day=man_day,
                status=et_status or "DRAFT",
                validator_username=validator,
                creator_username=creator,
                updator_username=updator,
                comment=comment,
                price_increase_reason=price_reason,
                creation_date=creation_dt,
                update_date=update_dt,
                site=site,
                month_period_label=mp_label,
            ))

    logger.info(
        f"Pipeline complete. "
        f"employee_times={len(result.employee_times)}, "
        f"employees={len(result.employees)}, "
        f"anomalies={len(result.anomalies)}"
    )
    return result


class _SkipRow(Exception):
    """Raised internally to skip the current row without logging a full traceback."""
    pass
