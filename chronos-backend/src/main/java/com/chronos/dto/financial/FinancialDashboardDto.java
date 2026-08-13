package com.chronos.dto.financial;

import java.util.List;

/**
 * Financial Officer dashboard response.
 * All numeric values are raw — formatting is done client-side.
 */
public record FinancialDashboardDto(
    FinancialStatCards statCards,
    List<CostByCompany> costByCompany,
    List<CostByOu> costByOu,
    List<ActivityNatureSplit> activityNatureSplit,
    List<CostTrendPoint> costTrend,
    List<AccountingCodeCost> topAccountingCodes
) {}