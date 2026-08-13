package com.chronos.dto.product;

/**
 * Man-days allocated to each product for the bar chart.
 */
public record ManDaysByProduct(
    String productName,
    Double manDays
) {}