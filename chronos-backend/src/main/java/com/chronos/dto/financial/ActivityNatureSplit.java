package com.chronos.dto.financial;

/**
 * Man-day split by activity nature for the donut chart.
 * Values are man-days (not percentages) — frontend computes percentages.
 */
public record ActivityNatureSplit(
    String natureName,
    Double manDays,
    String color
) {}