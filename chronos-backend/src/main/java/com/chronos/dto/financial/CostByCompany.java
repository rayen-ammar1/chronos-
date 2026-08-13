package com.chronos.dto.financial;

/**
 * Aggregated cost per company for the selected period.
 * Used for the "Cost by Company" bar chart (top 5).
 */
public record CostByCompany(
    String companyName,
    Double totalCost
) {}