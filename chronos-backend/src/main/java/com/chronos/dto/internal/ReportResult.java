package com.chronos.dto.internal;

import lombok.Value;
import java.util.List;

/**
 * Return type of ReportGenerationService.generateReport().
 * Both lists are fully populated before CsvExportService writes either file.
 */
@Value
public class ReportResult {
    List<ReportLine>   reportLines;
    List<AnomalyLine>  anomalyLines;

    public boolean hasAnomalies() {
        return anomalyLines != null && !anomalyLines.isEmpty();
    }
}
