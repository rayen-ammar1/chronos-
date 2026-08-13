"""
Central configuration.
All settings are read from environment variables or a .env file.
Copy .env.example to .env and fill in your values before running.
"""

import os
from dotenv import load_dotenv

load_dotenv()

# ── Database ──────────────────────────────────────────────────────────────────
DB_URL: str = os.getenv(
    "CHRONOS_DB_URL",
    "postgresql+psycopg2://postgres:postgres@localhost:5432/chronos"
)

# ── Processing ────────────────────────────────────────────────────────────────
BATCH_SIZE: int = int(os.getenv("BATCH_SIZE", "500"))

# Product name in source data that means "no product assigned"
NULL_PRODUCT_SENTINEL: str = os.getenv("NULL_PRODUCT_SENTINEL", "NA")

# Expected ratio of elapsed_time / man_day (8 hours = 1 day)
EXPECTED_HOURS_PER_DAY: float = float(os.getenv("EXPECTED_HOURS_PER_DAY", "8.0"))
RATIO_TOLERANCE: float = float(os.getenv("RATIO_TOLERANCE", "0.01"))

# ── Output paths ──────────────────────────────────────────────────────────────
ANOMALIES_OUTPUT_DIR: str = os.getenv("ANOMALIES_OUTPUT_DIR", "./output")
LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")
