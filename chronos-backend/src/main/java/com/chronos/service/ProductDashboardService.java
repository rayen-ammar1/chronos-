package com.chronos.service;

import com.chronos.dto.product.*;
import com.chronos.entity.MonthPeriod;
import com.chronos.repository.EmployeeByProductRepository;
import com.chronos.repository.EmployeeTimeRepository;
import com.chronos.repository.MonthPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Product Manager dashboard service.
 *
 * Provides aggregated KPIs and chart data for the product dashboard.
 * All queries use @Query aggregations grouped by MonthPeriod and exclude
 * ExcludedOrganizationalUnit OUs.
 *
 * Utilization = booked man-days / capacity (working days from CountryCalendar).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductDashboardService {

    private final MonthPeriodRepository monthPeriodRepository;
    private final EmployeeByProductRepository employeeByProductRepository;
    private final EmployeeTimeRepository employeeTimeRepository;

    /**
     * Builds the complete Product Manager dashboard for a given period.
     */
        public ProductDashboardDto getProductDashboard(Integer year, Integer month) {
        // 🚀 FIX: Use .orElse(null) instead of .orElseThrow()
        MonthPeriod period = monthPeriodRepository.findByYearAndMonth(year, month).orElse(null);

        if (period == null) {
            log.warn("MonthPeriod not found for {}/{}. Returning empty dashboard so the UI doesn't crash.", year, month);
            return new ProductDashboardDto(
                    new ProductStatCards(0, 0.0, 0.0, 0),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }

        LocalDate monthStart = period.getStartDate();
        LocalDate monthEnd = period.getEndDate();
        
        // ... leave the rest of the code exactly as it is ...

        // Stat cards
        ProductStatCards statCards = buildStatCards(year, month, monthStart, monthEnd);

        // Charts
        List<ManDaysByProduct> manDaysByProduct = buildManDaysByProduct(monthStart, monthEnd);
        List<ActivityNatureBreakdown> natureBreakdown = buildActivityNatureBreakdown(monthStart, monthEnd);
        List<TopProjectByManDays> topProjects = buildTopProjects(monthStart, monthEnd);
        List<ManDaysTrendPoint> manDaysTrend = buildManDaysTrend(year, month);
        List<HeadcountByCompany> headcountByCompany = buildHeadcountByCompany(monthStart, monthEnd);

        return new ProductDashboardDto(
                statCards, manDaysByProduct, natureBreakdown, topProjects,
                manDaysTrend, headcountByCompany
        );
    }

    // ── Stat cards ────────────────────────────────────────────────────────────

    private ProductStatCards buildStatCards(Integer year, Integer month,
                                             LocalDate monthStart, LocalDate monthEnd) {
        // Allocated headcount on products
        Integer headcount = employeeByProductRepository.countEmployeesWithProduct(monthStart, monthEnd);

        // Total man-days on products
        Double totalManDays = employeeByProductRepository.sumManDaysOnProducts(monthStart, monthEnd);
        if (totalManDays == null) totalManDays = 0.0;

        // Utilization = booked man-days / capacity
        Integer capacity = employeeByProductRepository.sumCapacityForProductEmployees(monthStart, monthEnd);
        double utilization = capacity > 0 ? (totalManDays / capacity) * 100.0 : 0.0;

        // Open anomalies on my products
        Integer openAnomalies = employeeTimeRepository.countAnomaliesByPeriod(year, month);

        return new ProductStatCards(
                headcount != null ? headcount : 0,
                round(totalManDays),
                round(utilization),
                openAnomalies != null ? openAnomalies : 0
        );
    }

    // ── Man-Days by Product (bar) ────────────────────────────────────────────

    private List<ManDaysByProduct> buildManDaysByProduct(LocalDate monthStart, LocalDate monthEnd) {
        List<Object[]> results = employeeByProductRepository.sumManDaysByProduct(monthStart, monthEnd);
        List<ManDaysByProduct> list = new ArrayList<>();
        for (Object[] row : results) {
            String productName = (String) row[0];
            double manDays = ((Number) row[1]).doubleValue();
            list.add(new ManDaysByProduct(productName, round(manDays)));
        }
        return list;
    }

    // ── Activity Nature breakdown (donut) ────────────────────────────────────

    private List<ActivityNatureBreakdown> buildActivityNatureBreakdown(LocalDate monthStart, LocalDate monthEnd) {
        List<Object[]> results = employeeByProductRepository.sumManDaysByActivityNatureForProducts(monthStart, monthEnd);
        List<ActivityNatureBreakdown> list = new ArrayList<>();

        var colorMap = java.util.Map.ofEntries(
                java.util.Map.entry("REGIE", "#C62828"),
                java.util.Map.entry("FORFAIT", "#EF5350"),
                java.util.Map.entry("SUPPORT", "#757575"),
                java.util.Map.entry("HOLIDAYS", "#FFB300"),
                java.util.Map.entry("MAINTENANCE", "#BDBDBD"),
                java.util.Map.entry("RPS", "#E57373")
        );

        for (Object[] row : results) {
            String nature = (String) row[0];
            double manDays = ((Number) row[1]).doubleValue();
            String color = colorMap.getOrDefault(nature, "#9E9E9E");
            list.add(new ActivityNatureBreakdown(nature, round(manDays), color));
        }
        return list;
    }

    // ── Top Projects by Man-Days (bar/table) ─────────────────────────────────

    private List<TopProjectByManDays> buildTopProjects(LocalDate monthStart, LocalDate monthEnd) {
        List<Object[]> results = employeeByProductRepository.sumManDaysByProject(monthStart, monthEnd);
        List<TopProjectByManDays> list = new ArrayList<>();
        for (Object[] row : results) {
            String projectName = (String) row[0];
            double manDays = ((Number) row[1]).doubleValue();
            list.add(new TopProjectByManDays(projectName, round(manDays)));
        }
        return list;
    }

    // ── Man-Days Trend (line, last 6 periods) ────────────────────────────────

    private List<ManDaysTrendPoint> buildManDaysTrend(Integer year, Integer month) {
        List<ManDaysTrendPoint> trend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            int m = month - i;
            int y = year;
            while (m <= 0) { m += 12; y--; }
            while (m > 12) { m -= 12; y++; }

            Double manDays = employeeByProductRepository.sumManDaysOnProductsByPeriod(y, m);
            if (manDays == null) manDays = 0.0;

            String periodLabel = String.format("%s %d",
                    java.time.Month.of(m).toString().substring(0, 3), y);
            trend.add(new ManDaysTrendPoint(periodLabel, round(manDays)));
        }
        return trend;
    }

    // ── Headcount by Company (bar) ───────────────────────────────────────────

    private List<HeadcountByCompany> buildHeadcountByCompany(LocalDate monthStart, LocalDate monthEnd) {
        List<Object[]> results = employeeByProductRepository.countHeadcountByCompany(monthStart, monthEnd);
        List<HeadcountByCompany> list = new ArrayList<>();
        for (Object[] row : results) {
            String companyName = (String) row[0];
            int headcount = ((Number) row[1]).intValue();
            list.add(new HeadcountByCompany(companyName, headcount));
        }
        return list;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}