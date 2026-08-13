package com.chronos.controller;

import com.chronos.dto.product.ProductDashboardDto;
import com.chronos.service.ProductDashboardService;
import com.chronos.repository.EmployeeTimeRepository;
import com.chronos.repository.EmployeeByProductRepository;
import com.chronos.repository.MonthPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Slf4j
@RestController
@RequestMapping("/api/dashboard/product")
@RequiredArgsConstructor
public class ProductDashboardController {

    private final ProductDashboardService productDashboardService;
    
    // 🚀 ADD THESE FOR DEBUGGING
    private final EmployeeTimeRepository employeeTimeRepository;
    private final EmployeeByProductRepository employeeByProductRepository;
    private final MonthPeriodRepository monthPeriodRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('PRODUCT_MANAGER', 'DATA_ADMIN')")
    public ResponseEntity<ProductDashboardDto> getDashboard(
            @RequestParam Integer year,
            @RequestParam Integer month) {
        log.info("Product dashboard requested for {}/{}", year, month);
        ProductDashboardDto dto = productDashboardService.getProductDashboard(year, month);
        return ResponseEntity.ok(dto);
    }

    // 🚀 ADD THIS METHOD: Prints counts to the terminal on startup!
   @EventListener(ApplicationReadyEvent.class)
public void printDebugCounts() {
    System.out.println("🔍 month_period=" + monthPeriodRepository.count()
        + " | employee_time=" + employeeTimeRepository.count()
        + " | employee_by_product=" + employeeByProductRepository.count());
}
}