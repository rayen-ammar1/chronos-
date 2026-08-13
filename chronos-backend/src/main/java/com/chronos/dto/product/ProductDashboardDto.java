package com.chronos.dto.product;

import java.util.List;

/**
 * Product Manager dashboard response.
 * All numeric values are raw — formatting is done client-side.
 */
public record ProductDashboardDto(
    ProductStatCards statCards,
    List<ManDaysByProduct> manDaysByProduct,
    List<ActivityNatureBreakdown> activityNatureBreakdown,
    List<TopProjectByManDays> topProjects,
    List<ManDaysTrendPoint> manDaysTrend,
    List<HeadcountByCompany> headcountByCompany
) {}