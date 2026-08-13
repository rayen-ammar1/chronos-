package com.chronos.controller;

import com.chronos.dto.Insight;
import com.chronos.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @GetMapping("/{year}/{month}")
    @PreAuthorize("hasAnyRole('DATA_ADMIN','FINANCIAL_OFFICER','PRODUCT_MANAGER')")
    public List<Insight> insights(@PathVariable Integer year, @PathVariable Integer month) {
        return insightService.getInsights(year, month);
    }
}