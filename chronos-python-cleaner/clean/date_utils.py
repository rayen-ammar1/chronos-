"""
Date normalization utilities.

The source CSVs contain dates in three formats:
  1. pandas Timestamp (most rows, properly parsed by openpyxl)
  2. String-encoded Excel serial — e.g. "44568.0"
     This is the critical mixed-type bug found in Employee Company End Date.
     Some cells arrive as floats stored as strings, not parsed as dates.
  3. NaN / None / NaT — represents missing/NULL dates

All public functions return a Python date object or None.
They never raise silently — callers get ValueError with context so the
anomaly detector can log the exact row and column that failed.
"""

import re
from datetime import date, datetime, timedelta
from typing import Optional

import pandas as pd


# Excel's epoch is 1899-12-30 (accounting for the 1900 leap-year bug).
_EXCEL_EPOCH = date(1899, 12, 30)

# Matches string-encoded serials like "44568.0" or "44568"
_EXCEL_SERIAL_RE = re.compile(r'^\d+(\.\d+)?$')

# Matches "1|2024" or "12|2024" — the MonthPeriod source format
_MONTH_PERIOD_RE = re.compile(r'^(\d{1,2})\|(\d{4})$')


def normalize_date(val) -> Optional[date]:
    """
    Normalize any date-like value from the source CSV to a Python date.

    Handles:
      - pandas Timestamp          → val.date()
      - datetime                  → val.date()
      - date                      → val (passthrough)
      - str "44568.0" (serial)    → EXCEL_EPOCH + 44568 days
      - str "2024-01-15" (ISO)    → date.fromisoformat(val)
      - str "2024-01-15 00:00:00" → date part only  🚀 NEW
      - None / NaN / NaT          → None

    Raises ValueError if the value is present but cannot be parsed.
    """
    if val is None:
        return None

    if isinstance(val, pd.Timestamp):
        if pd.isna(val):
            return None
        return val.date()

    if isinstance(val, datetime):
        return val.date()

    if isinstance(val, date):
        return val

    if isinstance(val, float):
        if pd.isna(val):
            return None
        # Float Excel serial (e.g. 44568.0 read as float by pandas)
        return _excel_serial_to_date(int(val))

    if isinstance(val, str):
        val = val.strip()
        if val == '' or val.lower() in ('nat', 'none', 'null', 'nan'):
            return None
        # 🚀 FIX: Excel exports 'YYYY-MM-DD HH:MM:SS' → keep only 'YYYY-MM-DD'
        val = val.replace('T', ' ').split(' ')[0]
        # String Excel serial e.g. "44568.0" — the critical mixed-type case
        if _EXCEL_SERIAL_RE.match(val):
            return _excel_serial_to_date(int(float(val)))
        # ISO format fallback
        try:
            return date.fromisoformat(val)
        except ValueError:
            raise ValueError(
                f"Cannot parse date string: {val!r}. "
                "Expected a date, ISO string, or Excel serial number."
            )

    if isinstance(val, int):
        return _excel_serial_to_date(val)

    raise TypeError(
        f"Unexpected type for date field: {type(val).__name__!r} = {val!r}"
    )


def normalize_datetime(val) -> Optional[datetime]:
    """
    Like normalize_date but returns a datetime (midnight if only date available).
    Used for creation_date and update_date fields in EmployeeTime.
    """
    d = normalize_date(val)
    if d is None:
        return None
    if isinstance(d, datetime):
        return d
    return datetime(d.year, d.month, d.day)


def parse_month_period(val) -> tuple[int, int, date, date, str]:
    """
    Parse the source MonthPeriod label "M|YYYY" into its components.

    Returns: (month, year, start_date, end_date, source_label)

    Examples:
        "1|2024"  → (1, 2024, date(2024,1,1), date(2024,1,31), "1|2024")
        "12|2023" → (12, 2023, date(2023,12,1), date(2023,12,31), "12|2023")

    Raises ValueError if the format does not match "M|YYYY".
    """
    from calendar import monthrange

    label = str(val).strip() if val is not None else ''
    match = _MONTH_PERIOD_RE.match(label)
    if not match:
        raise ValueError(
            f"Invalid MonthPeriod format: {label!r}. Expected 'M|YYYY' e.g. '1|2024'."
        )

    month = int(match.group(1))
    year = int(match.group(2))

    if not (1 <= month <= 12):
        raise ValueError(f"Month {month} out of range [1, 12] in: {label!r}")

    start = date(year, month, 1)
    _, last_day = monthrange(year, month)
    end = date(year, month, last_day)

    return month, year, start, end, label


def validate_date_range(start: Optional[date], end: Optional[date], context: str = "") -> Optional[str]:
    """
    Check that start <= end when both are present.
    Returns an error message string if invalid, None if OK.

    This catches the anomaly found in the source data where some CompanyMember
    rows have start_date (2023-07-xx) > end_date (2022-01-xx).
    """
    if start is None or end is None:
        return None
    if start > end:
        msg = f"start_date ({start}) is after end_date ({end})"
        return f"{msg} — {context}" if context else msg
    return None


# ── Private ───────────────────────────────────────────────────────────────────

def _excel_serial_to_date(serial: int) -> date:
    """
    Convert an Excel serial day number to a Python date.

    Excel uses 1899-12-30 as day 0 (with the deliberate 1900 leap-year bug).
    Day 1 = 1900-01-01, Day 44927 = 2023-01-01, etc.
    """
    if serial < 0:
        raise ValueError(f"Negative Excel serial not valid: {serial}")
    return _EXCEL_EPOCH + timedelta(days=serial)