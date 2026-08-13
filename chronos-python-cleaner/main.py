"""
Chronos Python Cleaning Service — CLI entry point.

Usage examples:

  # Full run: clean and load both source files
  python main.py \\
      --employee-time data/Book1.xlsx \\
      --org-assignment data/Organizational_Assignment.xlsx

  # Employee time only
  python main.py --employee-time data/Book1.xlsx

  # Org assignment only
  python main.py --org-assignment data/Organizational_Assignment.xlsx

  # Dry run — validate and produce anomaly CSV, do NOT write to DB
  python main.py --employee-time data/Book1.xlsx --dry-run

  # Override DB connection
  python main.py --employee-time data/Book1.xlsx \\
      --db-url postgresql+psycopg2://user:pass@host:5432/chronos
"""

import argparse
import logging
import os
import sys
from datetime import datetime

from config import DB_URL, LOG_LEVEL
from clean.employee_time_cleaner import clean_employee_time_file
from clean.org_assignment_cleaner import clean_org_assignment_file
from load.db_loader import ChronosDbLoader, write_anomalies_csv


def _setup_logging(level: str):
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s  %(levelname)-8s  %(name)s — %(message)s",
        datefmt="%H:%M:%S",
        handlers=[
            logging.StreamHandler(sys.stdout),
        ],
    )


def _merge_lists(*lists) -> list:
    """Merge multiple lists, deduplicating by object equality (used for lookup entities)."""
    seen = set()
    result = []
    for lst in lists:
        for item in lst:
            key = repr(item)
            if key not in seen:
                seen.add(key)
                result.append(item)
    return result


def run(
    employee_time_path: str = None,
    org_assignment_path: str = None,
    db_url: str = None,
    dry_run: bool = False,
):
    logger = logging.getLogger("chronos.main")
    start = datetime.now()
    logger.info("=" * 60)
    logger.info("Chronos cleaning service starting")
    logger.info(f"  employee_time    : {employee_time_path or '—'}")
    logger.info(f"  org_assignment   : {org_assignment_path or '—'}")
    logger.info(f"  dry_run          : {dry_run}")
    logger.info("=" * 60)

    if not employee_time_path and not org_assignment_path:
        logger.error("No input files provided. Use --employee-time and/or --org-assignment.")
        sys.exit(1)

    all_anomalies = []

    # ── Step 1: Clean ─────────────────────────────────────────────────────────

    et_result  = None
    oa_result  = None

    if employee_time_path:
        logger.info(f"[1/2] Cleaning employee time file ...")
        et_result = clean_employee_time_file(employee_time_path)
        all_anomalies.extend(et_result.anomalies)
        logger.info(
            f"      → {len(et_result.employee_times)} timesheet rows, "
            f"{len(et_result.employees)} employees, "
            f"{len(et_result.anomalies)} anomalies"
        )

    if org_assignment_path:
        logger.info(f"[2/2] Cleaning org assignment file ...")
        oa_result = clean_org_assignment_file(org_assignment_path)
        all_anomalies.extend(oa_result.anomalies)
        logger.info(
            f"      → {len(oa_result.assignments)} assignments, "
            f"{len(oa_result.anomalies)} anomalies"
        )

    # ── Step 2: Write anomalies CSV (always, even for dry runs) ──────────────

    if all_anomalies:
        errors   = [a for a in all_anomalies if a.severity == "ERROR"]
        warnings = [a for a in all_anomalies if a.severity == "WARNING"]
        logger.info(
            f"\nAnomaly summary: {len(errors)} errors, {len(warnings)} warnings"
        )
        anomaly_path = write_anomalies_csv(all_anomalies, run_label="combined")
        logger.info(f"Anomaly file: {anomaly_path}")
    else:
        logger.info("No anomalies detected.")

    if dry_run:
        logger.info("\nDry run — DB write skipped. Exiting.")
        return

    # ── Step 3: Load into PostgreSQL ──────────────────────────────────────────

    active_db_url = db_url or DB_URL
    logger.info(f"\nConnecting to database ...")
    loader = ChronosDbLoader(active_db_url)

    # Merge lookup entities from both pipelines (deduplication already handled
    # inside each cleaner; here we just combine the two result sets)
    load_kwargs = {}

    if et_result:
        load_kwargs.update({
            "employees":        et_result.employees,
            "companies":        et_result.companies,
            "company_members":  et_result.company_members,
            "products":         et_result.products,
            "clients":          et_result.clients,
            "billing_entities": et_result.billing_entities,
            "org_units":        et_result.organizational_units,
            "ou_members":       et_result.ou_members,
            "activity_natures": et_result.activity_natures,
            "accounting_codes": et_result.accounting_codes,
            "projects":         et_result.projects,
            "lots":             et_result.lots,
            "iterations":       et_result.iterations,
            "phases":           et_result.phases,
            "activities":       et_result.activities,
            "month_periods":    et_result.month_periods,
            "employee_times":   et_result.employee_times,
        })

    if oa_result:
        # Merge org-assignment entities on top of employee-time entities
        load_kwargs["employees"]     = _merge_lists(
            load_kwargs.get("employees", []), oa_result.employees
        )
        load_kwargs["org_units"]     = _merge_lists(
            load_kwargs.get("org_units", []), oa_result.organizational_units
        )
        load_kwargs["products"]      = _merge_lists(
            load_kwargs.get("products", []), oa_result.products
        )
        load_kwargs["accounting_codes"] = _merge_lists(
            load_kwargs.get("accounting_codes", []), oa_result.accounting_codes
        )
        load_kwargs["org_assignments"] = oa_result.assignments

    logger.info("Loading data into PostgreSQL ...")
    summary = loader.load_all(**load_kwargs)

    # ── Step 4: Print summary ─────────────────────────────────────────────────

    elapsed = (datetime.now() - start).total_seconds()
    logger.info("\n" + "=" * 60)
    logger.info("LOAD SUMMARY")
    logger.info("=" * 60)
    for entity, count in summary.items():
        logger.info(f"  {entity:<30} {count:>6} rows inserted")
    logger.info("-" * 60)
    logger.info(f"  Total anomalies                {len(all_anomalies):>6}")
    logger.info(f"  Elapsed time                   {elapsed:.1f}s")
    logger.info("=" * 60)

    # Exit with error code if there were ERROR-level anomalies
    error_count = sum(1 for a in all_anomalies if a.severity == "ERROR")
    if error_count > 0:
        logger.warning(
            f"\n{error_count} ERROR-level anomalies were found. "
            "Rows with errors were NOT inserted. Review the anomalies CSV."
        )
        sys.exit(2)

    logger.info("\nPipeline completed successfully.")


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Chronos data cleaning and loading service",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--employee-time", "-e",
        metavar="FILE",
        help="Path to the employee time Excel/CSV file (Book1.xlsx style)",
    )
    parser.add_argument(
        "--org-assignment", "-o",
        metavar="FILE",
        help="Path to the org assignment Excel file",
    )
    parser.add_argument(
        "--db-url",
        metavar="URL",
        help="Override the PostgreSQL connection URL from config/env",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate and produce anomaly CSV without writing to the database",
    )
    parser.add_argument(
        "--log-level",
        default=LOG_LEVEL,
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="Logging verbosity (default: INFO)",
    )

    args = parser.parse_args()
    _setup_logging(args.log_level)

    run(
        employee_time_path=args.employee_time,
        org_assignment_path=args.org_assignment,
        db_url=args.db_url,
        dry_run=args.dry_run,
    )


if __name__ == "__main__":
    main()
