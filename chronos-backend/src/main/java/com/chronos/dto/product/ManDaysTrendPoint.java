package com.chronos.dto.product;

/**
 * One point on the 6-period man-days trend line chart.
 */
public record ManDaysTrendPoint(
    String period,
    Double manDays
) {}