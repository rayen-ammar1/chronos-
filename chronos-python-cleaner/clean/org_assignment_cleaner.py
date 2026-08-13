"""
Organizational Assignment cleaning pipeline.

Reads the Org Assignment source file (Organizational_Assignment_ANONYMIZED.xlsx)
and produces CleanOrganizationalAssignment records.

Also extracts CleanEmployee, CleanOrganizationalUnit, CleanAccountingCode,
and CleanProduct records for any entities that appear here but not in the
Employee Time file.

Column layout observed in source:
  Employee | Organizational Unit | Accounting Code | Product | Allocation Percentage
"""

import logging
from dataclasses import dataclass, field

import pandas as pd

from clean.validators import (
    normalize_string,
    normalize_product_name,
    check_allocation_percentage,
    check_required_string,
    check_split_allocation_totals,
)
from models.clean_records import (
    AnomalyRecord,
    CleanAccountingCode,
    CleanEmployee,
    CleanOrganizationalAssignment,
    CleanOrganizationalUnit,
    CleanProduct,
)

logger = logging.getLogger(__name__)

COLUMN_MAP = {
    "Employee":              "employee_identifier",
    "Organizational Unit":   "ou_name",
    "Accounting Code":       "acc_identifier",
    "Product":               "product_name",
    "Allocation Percentage": "allocation_percentage",
}


@dataclass
class OrgAssignmentCleanResult:
    employees:            list[CleanEmployee]                = field(default_factory=list)
    organizational_units: list[CleanOrganizationalUnit]      = field(default_factory=list)
    accounting_codes:     list[CleanAccountingCode]          = field(default_factory=list)
    products:             list[CleanProduct]                 = field(default_factory=list)
    assignments:          list[CleanOrganizationalAssignment] = field(default_factory=list)
    anomalies:            list[AnomalyRecord]               = field(default_factory=list)


def clean_org_assignment_file(filepath: str) -> OrgAssignmentCleanResult:
    """
    Main entry point for the org assignment pipeline.
    """
    logger.info(f"Reading org assignment file: {filepath}")
    df = pd.read_excel(filepath, dtype=str)
    df = df.rename(columns=COLUMN_MAP)
    logger.info(f"Loaded {len(df)} rows")

    result = OrgAssignmentCleanResult()

    seen_employees: set[str]   = set()
    seen_ous:       set[str]   = set()
    seen_codes:     set[str]   = set()
    seen_products:  set[str]   = set()

    for idx, raw in df.iterrows():
        row_num = int(idx) + 2
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

        try:
            emp_id  = normalize_string(raw.get("employee_identifier"))
            ou_name = normalize_string(raw.get("ou_name"))
            acc_id  = normalize_string(raw.get("acc_identifier"))
            pct_raw = raw.get("allocation_percentage")

            # Required field checks
            for val, name in [
                (emp_id,  "Employee"),
                (ou_name, "Organizational Unit"),
                (acc_id,  "Accounting Code"),
            ]:
                err = check_required_string(val, name, src, row_num)
                if err:
                    result.anomalies.append(err)
                    raise _SkipRow(f"Missing: {name}")

            pct_err = check_allocation_percentage(pct_raw, src, row_num)
            if pct_err:
                result.anomalies.append(pct_err)
                raise _SkipRow("Invalid allocation percentage")

            allocation_pct = float(pct_raw)
            product_name   = normalize_product_name(raw.get("product_name"))

        except _SkipRow as e:
            logger.debug(f"Row {row_num} skipped: {e}")
            continue
        except Exception as e:
            anomaly("(multiple)", f"Unexpected parse error: {e}", "ERROR", "")
            continue

        # Accumulate lookup entities
        if emp_id not in seen_employees:
            seen_employees.add(emp_id)
            result.employees.append(CleanEmployee(
                identifier=emp_id,
                first_name="",   # not available in this file — merged with Employee Time data
                last_name="",
            ))

        if ou_name not in seen_ous:
            seen_ous.add(ou_name)
            result.organizational_units.append(CleanOrganizationalUnit(
                name=ou_name, parent_name=None
            ))

        if product_name and product_name not in seen_products:
            seen_products.add(product_name)
            result.products.append(CleanProduct(name=product_name))

        if acc_id not in seen_codes:
            seen_codes.add(acc_id)
            # Readable short codes from org assignment (e.g. "DAF_FIN")
            # billing_mode and other fields are not in this file — loader
            # will do an UPDATE if a matching acc code already exists from
            # the Employee Time pipeline, otherwise inserts with defaults.
            result.accounting_codes.append(CleanAccountingCode(
                operational_identifier=acc_id,
                billing_mode="NOTAPPLICABLE",  # placeholder — update from ET pipeline if exists
                billable=False,
                activity_nature_name="",
                organizational_unit_name=ou_name,
                product_name=product_name,
            ))

        result.assignments.append(CleanOrganizationalAssignment(
            employee_identifier=emp_id,
            organizational_unit_name=ou_name,
            accounting_code_identifier=acc_id,
            product_name=product_name,
            allocation_percentage=allocation_pct,
        ))

    # Post-processing: check split allocation totals
    split_warnings = check_split_allocation_totals(result.assignments, filepath)
    result.anomalies.extend(split_warnings)

    logger.info(
        f"Org assignment pipeline complete. "
        f"assignments={len(result.assignments)}, "
        f"anomalies={len(result.anomalies)}"
    )
    return result


class _SkipRow(Exception):
    pass
