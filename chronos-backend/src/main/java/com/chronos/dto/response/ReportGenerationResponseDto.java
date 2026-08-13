package com.chronos.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReportGenerationResponseDto {
    Integer year;
    Integer month;
    Integer totalReportLines;
    Integer totalAnomalyLines;
    String  reportDownloadUrl;
    String  anomaliesDownloadUrl;
    String  generatedAt;
}
