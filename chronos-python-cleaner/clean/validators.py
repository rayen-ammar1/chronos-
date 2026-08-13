"""
Validation rules and anomaly detection.

Every row passes through validate_employee_time_row() or
validate_org_assignment_row() before being accepted into the clean set.
Any failure appends an AnomalyRecord instead of a clean record.

Severity levels:
  ERROR   — row is rejected, written to Anomalies CSV, never inserted into DB.
  WARNING — row is inserted but flagged; the anomaly is also logged for review.
"""

from datetime import date
from typing import Optional

from config import EXPECTED_HOURS_PER_DAY, RATIO_TOLERANCE
from models.clean_records import AnomalyRecord

# ── Enum value maps ───────────────────────────────────────────────────────────

TIMESHEET_STATUS_MAP = {
    'closed':    'CLOSED',
    'draft':     'DRAFT',
    'submitted': 'SUBMITTED',
    'validated': 'VALIDATED',
    'rejected':  'REJECTED',
}

BILLING_MODE_MAP = {
    'notapplicable':      'NOTAPPLICABLE',
    'not applicable':     'NOTAPPLICABLE',
    'not_applicable':     'NOTAPPLICABLE',
    'fixed_price':        'FIXED_PRICE',
    'fixedprice':         'FIXED_PRICE',
    'forfait':            'FIXED_PRICE',
    'time_and_material':  'TIME_AND_MATERIAL',
    'timeandmaterial':    'TIME_AND_MATERIAL',
    'regie':              'TIME_AND_MATERIAL',
    'not_billable':       'NOT_BILLABLE',
    'notbillable':        'NOT_BILLABLE',
}

PROJECT_STATUS_MAP = {
    'active':    'ACTIVE',
    'completed': 'COMPLETED',
    'on_hold':   'ON_HOLD',
    'onhold':    'ON_HOLD',
    'cancelled': 'CANCELLED',
    'canceled':  'CANCELLED',
}

VALID_BILLING_MODES  = set(BILLING_MODE_MAP.values())
VALID_STATUSES       = set(TIMESHEET_STATUS_MAP.values())
VALID_PROJECT_STATUS = set(PROJECT_STATUS_MAP.values())


# ── Public normalizers ────────────────────────────────────────────────────────

def normalize_timesheet_status(val) -> Optional[str]:
    """
    Normalize timesheet status to enum value.
    Source data observed: "Closed" → must become "CLOSED".
    Returns None if val is blank (not an anomaly — status can be absent).
    Raises ValueError for unrecognised non-blank values.
    """
    if val is None or (isinstance(val, float) and _is_nan(val)):
        return None
    cleaned = str(val).strip().lower()
    if cleaned == '':
        return None
    result = TIMESHEET_STATUS_MAP.get(cleaned)
    if result is None:
        raise ValueError(
            f"Unknown TimesheetStatus: {val!r}. "
            f"Valid values (case-insensitive): {list(TIMESHEET_STATUS_MAP.keys())}"
        )
    return result


def normalize_billing_mode(val) -> str:
    """
    Normalize billing mode to enum value.
    Source data observed: "NOTAPPLICABLE" — must match exactly.
    Raises ValueError for unrecognised values (required field).
    """
    if val is None or (isinstance(val, float) and _is_nan(val)):
        raise ValueError("billing_mode is required but was NULL")
    cleaned = str(val).strip().lower()
    result = BILLING_MODE_MAP.get(cleaned)
    if result is None:
        raise ValueError(
            f"Unknown BillingMode: {val!r}. "
            f"Valid values (case-insensitive): {list(BILLING_MODE_MAP.keys())}"
        )
    return result


def normalize_project_status(val) -> Optional[str]:
    """
    Normalize project status to enum value or None.
    Source data observed: NULL in all sample rows — None is acceptable.
    """
    if val is None or (isinstance(val, float) and _is_nan(val)):
        return None
    cleaned = str(val).strip().lower()
    if cleaned == '':
        return None
    result = PROJECT_STATUS_MAP.get(cleaned)
    if result is None:
        raise ValueError(
            f"Unknown ProjectStatus: {val!r}. "
            f"Valid values: {list(PROJECT_STATUS_MAP.keys())}"
        )
    return result


def normalize_bool(val) -> bool:
    """Parse boolean-like values from CSV (true/false/1/0/yes/no)."""
    if isinstance(val, bool):
        return val
    if isinstance(val, (int, float)):
        return bool(val)
    if isinstance(val, str):
        return val.strip().lower() in ('true', '1', 'yes')
    return False


def normalize_string(val, max_length: int = None) -> Optional[str]:
    """Strip and return string, or None if blank/null."""
    if val is None or (isinstance(val, float) and _is_nan(val)):
        return None
    cleaned = str(val).strip()
    if cleaned == '':
        return None
    if max_length and len(cleaned) > max_length:
        cleaned = cleaned[:max_length]
    return cleaned


def normalize_product_name(val) -> Optional[str]:
    """
    Map "NA" product sentinel to None.
    Any other non-blank value is returned as-is.
    """
    s = normalize_string(val)
    if s is None or s.upper() == 'NA':
        return None
    return s


# ── Anomaly detection rules ───────────────────────────────────────────────────

def check_company_member_dates(
    start_date: Optional[date],
    end_date: Optional[date],
    source_file: str,
    source_row: int,
) -> Optional[AnomalyRecord]:
    """
    Rule: start_date must not be after end_date.
    Found in 2 out of 14 sample rows — likely data entry error or
    Excel serial parsing failure in the source system.
    """
    if start_date and end_date and start_date > end_date:
        return AnomalyRecord(
            source_file=source_file,
            source_row=source_row,
            column="Employee Company Start Date / End Date",
            issue=f"start_date ({start_date}) is after end_date ({end_date}). "
                  "Row rejected — check source data for this employee.",
            severity="ERROR",
            raw_value=f"start={start_date}, end={end_date}",
            context="CompanyMember date range anomaly. Possible causes: "
                    "swapped columns in source, or string Excel serial "
                    "not properly converted by source system.",
        )
    return None


def check_man_day_ratio(
    elapsed_time: float,
    man_day: float,
    source_file: str,
    source_row: int,
) -> Optional[AnomalyRecord]:
    """
    Rule: elapsed_time / man_day should equal 8.0 (8 hours = 1 business day).
    A significant deviation suggests a data entry error or non-standard day.
    This is a WARNING — the row is still inserted but flagged.
    """
    if man_day and man_day > 0:
        ratio = elapsed_time / man_day
        if abs(ratio - EXPECTED_HOURS_PER_DAY) > RATIO_TOLERANCE:
            return AnomalyRecord(
                source_file=source_file,
                source_row=source_row,
                column="Elapsed Time / Man Day",
                issue=f"Unexpected ratio: {elapsed_time} hours / {man_day} days "
                      f"= {ratio:.2f} (expected {EXPECTED_HOURS_PER_DAY}). "
                      "Row inserted but flagged for review.",
                severity="WARNING",
                raw_value=f"elapsed_time={elapsed_time}, man_day={man_day}",
            )
    return None


def check_man_day_present(
    man_day,
    source_file: str,
    source_row: int,
) -> Optional[AnomalyRecord]:
    """
    Rule: man_day must be present and > 0.
    This is the reporting unit. A NULL or zero man_day makes the row
    unusable for report calculations — reject it.
    """
    if man_day is None or (isinstance(man_day, float) and _is_nan(man_day)):
        return AnomalyRecord(
            source_file=source_file,
            source_row=source_row,
            column="Man Day",
            issue="man_day is NULL. Row rejected — cannot be used in report calculations.",
            severity="ERROR",
            raw_value=str(man_day),
        )
    if float(man_day) <= 0:
        return AnomalyRecord(
            source_file=source_file,
            source_row=source_row,
            column="Man Day",
            issue=f"man_day is {man_day} (must be > 0). Row rejected.",
            severity="ERROR",
            raw_value=str(man_day),
        )
    return None


def check_required_string(
    val,
    column_name: str,
    source_file: str,
    source_row: int,
) -> Optional[AnomalyRecord]:
    """Rule: required string field must not be blank."""
    if val is None or (isinstance(val, float) and _is_nan(val)) or str(val).strip() == '':
        return AnomalyRecord(
            source_file=source_file,
            source_row=source_row,
            column=column_name,
            issue=f"Required field '{column_name}' is missing or blank. Row rejected.",
            severity="ERROR",
            raw_value=str(val),
        )
    return None


def check_allocation_percentage(
    pct: float,
    source_file: str,
    source_row: int,
) -> Optional[AnomalyRecord]:
    """Rule: allocation_percentage must be > 0 and <= 100."""
    try:
        pct_f = float(pct)
    except (TypeError, ValueError):
        return AnomalyRecord(
            source_file=source_file,
            source_row=source_row,
            column="Allocation Percentage",
            issue=f"Cannot parse allocation percentage: {pct!r}. Row rejected.",
            severity="ERROR",
            raw_value=str(pct),
        )
    if not (0 < pct_f <= 100):
        return AnomalyRecord(
            source_file=source_file,
            source_row=source_row,
            column="Allocation Percentage",
            issue=f"Allocation percentage {pct_f} is outside (0, 100]. Row rejected.",
            severity="ERROR",
            raw_value=str(pct),
        )
    return None


def check_split_allocation_totals(
    records: list,
    source_file: str,
) -> list[AnomalyRecord]:
    """
    Post-processing rule: for each employee, the sum of all
    allocation_percentage values should equal 100.
    Deviations are flagged as WARNINGs (rows still inserted).

    OPEN QUESTION: partial allocation (<100%) may be intentional.
    This check produces warnings, not errors, until confirmed.
    """
    from collections import defaultdict
    totals: dict[str, float] = defaultdict(float)
    for r in records:
        totals[r.employee_identifier] += r.allocation_percentage

    anomalies = []
    for emp_id, total in totals.items():
        if abs(total - 100.0) > 0.01:
            anomalies.append(AnomalyRecord(
                source_file=source_file,
                source_row=-1,
                column="Allocation Percentage",
                issue=f"Employee {emp_id}: org assignment percentages sum to "
                      f"{total:.2f}% (expected 100%). "
                      "Row(s) inserted but flagged. "
                      "OPEN QUESTION: is partial allocation intentional?",
                severity="WARNING",
                raw_value=f"employee={emp_id}, total={total:.2f}%",
            ))
    return anomalies


# ── Private ───────────────────────────────────────────────────────────────────

def _is_nan(val) -> bool:
    try:
        import math
        return math.isnan(val)
    except (TypeError, ValueError):
        return False
