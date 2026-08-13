package com.chronos.controller;

import com.chronos.dto.financial.FinancialDashboardDto;
import com.chronos.service.FinancialDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/dashboard/financial")
@RequiredArgsConstructor
public class FinancialDashboardController {

    private final FinancialDashboardService financialDashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCIAL_OFFICER', 'DATA_ADMIN')")
    public ResponseEntity<FinancialDashboardDto> getDashboard(
            @RequestParam Integer year,
            @RequestParam Integer month) {

        log.info("Financial dashboard requested for {}/{}", year, month);
        FinancialDashboardDto dto = financialDashboardService.getFinancialDashboard(year, month);
        return ResponseEntity.ok(dto);
    }
}