package com.chronos.dto.product;

/**
 * Stat card values for the Product Manager dashboard.
 * All values are raw numbers — the frontend formats them.
 */
public record ProductStatCards(
    Integer allocatedHeadcount,
    Double totalManDays,
    Double utilization,
    Integer openAnomalies
) {}