package com.chronos.dto.product;

/**
 * Headcount allocated to products per company for the bar chart.
 */
public record HeadcountByCompany(
    String companyName,
    Integer headcount
) {}