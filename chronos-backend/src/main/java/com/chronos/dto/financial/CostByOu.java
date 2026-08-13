package com.chronos.dto.financial;

/**
 * Aggregated cost per organizational unit for the selected period.
 * Used for the "Cost by OU" progress bars.
 */
public record CostByOu(
    String ouName,
    Double totalCost,
    Double percentage
) {}