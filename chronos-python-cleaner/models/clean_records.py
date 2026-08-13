from dataclasses import dataclass, field
from datetime import date, datetime
from typing import Optional

@dataclass
class CleanEmployee:
    identifier: str
    first_name: str
    last_name: str

@dataclass
class CleanCompany:
    name: str
    country: str

@dataclass
class CleanCompanyMember:
    employee_identifier: str
    company_name: str
    registration_number: Optional[str]
    start_date: date
    end_date: Optional[date]      # NULL = still active

@dataclass
class CleanProduct:
    name: str

@dataclass
class CleanClient:
    name: str

@dataclass
class CleanBillingEntity:
    name: str

@dataclass
class CleanOrganizationalUnit:
    name: str
    parent_name: Optional[str]

@dataclass
class CleanOrganizationalUnitMember:
    employee_identifier: str
    organizational_unit_name: str
    start_date: date
    end_date: Optional[date]

@dataclass
class CleanActivityNature:
    name: str

@dataclass
class CleanAccountingCode:
    operational_identifier: str
    billing_mode: str
    billable: bool
    activity_nature_name: str
    organizational_unit_name: Optional[str]
    product_name: Optional[str]

@dataclass
class CleanProject:
    name: str
    project_manager_username: Optional[str]
    status: Optional[str]
    client_name: Optional[str]
    billing_entity_name: Optional[str]

@dataclass
class CleanLot:
    name: str
    project_name: str

@dataclass
class CleanIteration:
    name: str
    lot_name: str
    project_name: str

@dataclass
class CleanPhase:
    name: str
    deliverable_name: Optional[str]
    is_capitalizable: bool
    capitalizable_date: Optional[date]
    is_capitalizable_by: Optional[str]   # PENDING business clarification
    iteration_name: str
    lot_name: str
    project_name: str

@dataclass
class CleanActivity:
    name: str
    phase_name: str
    iteration_name: str
    lot_name: str
    project_name: str

@dataclass
class CleanMonthPeriod:
    year: int
    month: int
    start_date: date
    end_date: date
    source_label: str             # original "1|2024" for traceability

@dataclass
class CleanEmployeeTime:
    employee_identifier: str
    accounting_code_identifier: str
    date: date
    elapsed_time: float           # hours — do NOT use for report logic
    man_day: float                # business days — THE reporting unit
    status: str                   # TimesheetStatus enum value
    validator_username: Optional[str]
    creator_username: Optional[str]
    updator_username: Optional[str]
    comment: Optional[str]
    price_increase_reason: Optional[str]
    creation_date: Optional[datetime]
    update_date: Optional[datetime]
    site: Optional[str]
    month_period_label: str

@dataclass
class CleanOrganizationalAssignment:
    employee_identifier: str
    organizational_unit_name: str
    accounting_code_identifier: str
    product_name: Optional[str]   # None when source was "NA"
    allocation_percentage: float

@dataclass
class AnomalyRecord:
    source_file: str
    source_row: int
    column: str
    issue: str
    severity: str                 # ERROR | WARNING
    raw_value: str
    context: str = ""
