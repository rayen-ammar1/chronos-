package com.chronos.dto.internal;

import lombok.Builder;
import lombok.Value;

/**
 * One line written to the Report CSV output.
 *
 * prefix is "NO_TS_" for rows generated from default data (no timesheets
 * or incomplete holiday months). Empty string for timesheet-derived rows.
 *
 * accountingCodeIdentifier is "NA" for full-holiday-month rows
 * (AccountingCode not applicable).
 */
@Value
@Builder
public class ReportLine {
    String  employeeIdentifier;
    String  firstName;
    String  lastName;
    String  registrationNumber;
    String  companyName;
    String  ouName;
    String  parentOuName;
    String  productName;
    String  activityNatureName;
    String  accountingCodeIdentifier;
    Double  manDays;
    Double  ratio;
    String  prefix;              // "NO_TS_" or ""
    Integer year;
    Integer month;
}
