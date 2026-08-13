package com.chronos.dto.product;

/**
 * Activity nature breakdown for the donut chart.
 * Values are man-days — frontend computes percentages.
 */
public record ActivityNatureBreakdown(
    String natureName,
    Double manDays,
    String color
) {}