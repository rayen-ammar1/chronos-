package com.chronos.dto.internal;

import java.time.LocalDate;

/**
 * Result of the native SQL query that fetches CompanyMembers active during
 * a MonthPeriod WITH their resolved search dates pre-computed by the DB.
 *
 * searchStart = GREATEST(cm.start_date, monthPeriod.startDate)
 * searchEnd   = LEAST(COALESCE(cm.end_date, monthPeriod.endDate), monthPeriod.endDate)
 *
 * These two fields collapse all 6 cases from spec Table 1 into one expression.
 * Every downstream query (timesheets, OU member, capacity) uses searchStart/searchEnd,
 * never the raw CompanyMember dates.
 */
public interface CompanyMemberSearchRange {
    Long   getCompanyMemberId();
    Long   getEmployeeId();
    Long   getCompanyId();
    String getRegistrationNumber();
    LocalDate getStartDate();
    LocalDate getEndDate();
    LocalDate getSearchStart();   // GREATEST(start_date, monthStartDate)
    LocalDate getSearchEnd();     // LEAST(COALESCE(end_date, monthEndDate), monthEndDate)
}
