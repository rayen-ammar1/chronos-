package com.chronos.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DashboardSummaryDto {
    Integer totalEmployees;
    Integer totalProjects;
    Integer totalCompanyMembers;
    String  lastGeneratedPeriod;
}
