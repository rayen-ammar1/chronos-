package com.chronos.dto.financial;

/**
 * Stat card values for the Financial Officer dashboard.
 * All values are raw numbers — the frontend formats them.
 */
public record FinancialStatCards(
    Double totalLaborCost,
    Double billableRatio,
    Integer anomalyCount,
    Double anomalyCostImpact,
    Double budgetVariance
) {}