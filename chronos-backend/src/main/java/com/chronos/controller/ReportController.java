package com.chronos.controller;

import com.chronos.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/{year}_{month}/report")
    @PreAuthorize("hasAnyRole('FINANCIAL_OFFICER','DATA_ADMIN')")
    public ResponseEntity<byte[]> financialReport(@PathVariable Integer year, @PathVariable Integer month) {
        return csv(reportService.buildFinancialReport(year, month),
                   "financial_report_" + year + "_" + month + ".csv");
    }

    @GetMapping("/{year}_{month}/anomalies")
    @PreAuthorize("hasAnyRole('FINANCIAL_OFFICER','DATA_ADMIN')")
    public ResponseEntity<byte[]> anomaliesReport(@PathVariable Integer year, @PathVariable Integer month) {
        return csv(reportService.buildAnomaliesReport(year, month),
                   "anomalies_" + year + "_" + month + ".csv");
    }

    private ResponseEntity<byte[]> csv(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}