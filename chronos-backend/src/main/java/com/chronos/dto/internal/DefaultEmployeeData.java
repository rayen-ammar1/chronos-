package com.chronos.dto.internal;

import lombok.Builder;
import lombok.Value;

import java.util.Optional;

/**
 * Resolved default cost-allocation context for an employee.
 * Used in the "no timesheets" and "holidays only" fallback paths.
 * All three fields must be present for a report line to be written;
 * if any is missing the row goes to the anomaly output instead.
 */
@Value
@Builder
public class DefaultEmployeeData {
    String ouName;
    String parentOuName;
    String productName;          // null if employee has no product assignment
    String activityNatureName;
    String accountingCodeIdentifier;
}
