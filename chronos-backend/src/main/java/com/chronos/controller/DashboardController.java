package com.chronos.controller;

import com.chronos.dto.response.DashboardSummaryDto;
import com.chronos.repository.CompanyMemberRepository;
import com.chronos.repository.EmployeeRepository;
import com.chronos.repository.MonthPeriodRepository;
import com.chronos.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final EmployeeRepository      employeeRepository;
    private final ProjectRepository       projectRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final MonthPeriodRepository   monthPeriodRepository;

    @GetMapping("/summary")
   
    public ResponseEntity<DashboardSummaryDto> summary() {
        String lastPeriod = monthPeriodRepository.findAll().stream()
                .max((a, b) -> {
                    int yearCmp = a.getYear().compareTo(b.getYear());
                    return yearCmp != 0 ? yearCmp : a.getMonth().compareTo(b.getMonth());
                })
                .map(p -> p.getMonth() + "/" + p.getYear())
                .orElse("—");

        return ResponseEntity.ok(DashboardSummaryDto.builder()
                .totalEmployees((int) employeeRepository.count())
                .totalProjects((int) projectRepository.count())
                .totalCompanyMembers((int) companyMemberRepository.count())
                .lastGeneratedPeriod(lastPeriod)
                .build());
    }

    /**
     * Admin-only dashboard endpoint.
     * Returns system-wide statistics for the DATA_ADMIN role.
     */
    @GetMapping("/admin/stats")
   
    public ResponseEntity<DashboardSummaryDto> adminStats() {
        return ResponseEntity.ok(DashboardSummaryDto.builder()
                .totalEmployees((int) employeeRepository.count())
                .totalProjects((int) projectRepository.count())
                .totalCompanyMembers((int) companyMemberRepository.count())
                .lastGeneratedPeriod("—")
                .build());
    }
}
