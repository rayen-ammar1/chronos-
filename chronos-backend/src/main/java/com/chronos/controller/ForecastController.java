package com.chronos.controller;

import com.chronos.dto.ForecastResponse;
import com.chronos.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;

    @GetMapping("/cost/{year}/{month}")
    @PreAuthorize("hasAnyRole('DATA_ADMIN','FINANCIAL_OFFICER','PRODUCT_MANAGER')")
    public ForecastResponse forecastCost(@PathVariable int year, @PathVariable int month) {
        return forecastService.forecastCost(year, month);
    }
}