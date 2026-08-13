package com.chronos.config;

import com.chronos.repository.EmployeeTimeRepository;
import com.chronos.repository.EmployeeByProductRepository;
import com.chronos.repository.MonthPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DbAudit {
    private final MonthPeriodRepository monthPeriodRepository;
    private final EmployeeTimeRepository employeeTimeRepository;
    private final EmployeeByProductRepository employeeByProductRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void audit() {
        System.out.println("\n==========================================");
        System.out.println("🔍 DB AUDIT: month_period rows       = " + monthPeriodRepository.count());
        System.out.println("🔍 DB AUDIT: employee_time rows       = " + employeeTimeRepository.count());
        System.out.println("🔍 DB AUDIT: employee_by_product rows = " + employeeByProductRepository.count());
        System.out.println("==========================================\n");
    }
}