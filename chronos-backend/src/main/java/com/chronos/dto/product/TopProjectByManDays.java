package com.chronos.dto.product;

/**
 * Top projects by man-days for the bar/table chart.
 */
public record TopProjectByManDays(
    String projectName,
    Double manDays
) {}