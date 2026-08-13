package com.chronos.dto.internal;

/**
 * Projection result of the GROUP BY accounting_code query on employee_time.
 * man_day is the ONLY field used — elapsed_time is intentionally excluded.
 */
public interface TimesheetSummary {
    Long   getAccountingCodeId();
    Double getTotalManDays();
    Long   getEntryCount();
}
