package com.chronos.dto.financial;

/**
 * Top accounting codes by total cost for the table.
 */
public record AccountingCodeCost(
    String operationalIdentifier,
    String companyName,
    String ouName,
    String activityNature,
    Double manDays,
    Double totalCost
) {}