package com.chronos.dto.financial;

/**
 * One point on the 6-period cost trend line chart.
 */
public record CostTrendPoint(
    String period,
    Double totalCost
) {}