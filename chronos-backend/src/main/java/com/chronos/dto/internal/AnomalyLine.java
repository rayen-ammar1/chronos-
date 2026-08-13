package com.chronos.dto.internal;

import lombok.Builder;
import lombok.Value;

/**
 * One line written to the Anomalies CSV output.
 * Generated when default data cannot be resolved for an employee.
 */
@Value
@Builder
public class AnomalyLine {
    String  employeeIdentifier;
    String  firstName;
    String  lastName;
    String  registrationNumber;
    String  companyName;
    String  missingField;        // e.g. "OrganizationalUnit", "Product", "ActivityNature"
    String  issue;
    Integer year;
    Integer month;
}
