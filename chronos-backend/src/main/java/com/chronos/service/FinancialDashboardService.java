package com.chronos.service;

import com.chronos.dto.financial.*;
import com.chronos.entity.MonthPeriod;
import com.chronos.repository.EmployeeTimeRepository;
import com.chronos.repository.MonthPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialDashboardService {

    private static final double DEFAULT_DAY_RATE = 700.0; 

    private final MonthPeriodRepository monthPeriodRepository;
    private final EmployeeTimeRepository employeeTimeRepository;

    public FinancialDashboardDto getFinancialDashboard(Integer year, Integer month) {
        MonthPeriod period = monthPeriodRepository.findByYearAndMonth(year, month).orElse(null);

        if (period == null) {
            log.warn("MonthPeriod not found for {}/{}. Returning empty financial dashboard.", year, month);
            return new FinancialDashboardDto(
                    new FinancialStatCards(0.0, 0.0, 0, 0.0, 0.0),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }

        LocalDate monthStart = period.getStartDate();
        LocalDate monthEnd = period.getEndDate();

        log.info("Building financial dashboard for {}/{}", year, month);

        FinancialStatCards statCards = buildStatCards(year, month, monthStart, monthEnd);
        List<CostByCompany> costByCompany = buildCostByCompany(monthStart, monthEnd);
        List<CostByOu> costByOu = buildCostByOu(monthStart, monthEnd);
        List<ActivityNatureSplit> natureSplit = buildActivityNatureSplit(monthStart, monthEnd);
        List<CostTrendPoint> costTrend = buildCostTrend(year, month);
        List<AccountingCodeCost> topAccCodes = buildTopAccountingCodes(monthStart, monthEnd);

        return new FinancialDashboardDto(
                statCards, costByCompany, costByOu, natureSplit, costTrend, topAccCodes
        );
    }

    private FinancialStatCards buildStatCards(Integer year, Integer month,
                                               LocalDate monthStart, LocalDate monthEnd) {
        Double totalManDays = employeeTimeRepository.sumTotalManDays(monthStart, monthEnd);
        if (totalManDays == null) totalManDays = 0.0;

        double totalLaborCost = totalManDays * DEFAULT_DAY_RATE;

        // 🚀 FIX: Bulletproof extraction of billable vs total man-days
        double billableManDays = 0.0;
        double totalManDaysForRatio = 0.0;
        
        try {
            Object rawResult = employeeTimeRepository.sumBillableVsTotal(monthStart, monthEnd);
            if (rawResult instanceof List) {
                List<?> list = (List<?>) rawResult;
                if (!list.isEmpty() && list.get(0) instanceof Object[]) {
                    Object[] row = (Object[]) list.get(0);
                    billableManDays = row[0] != null ? ((Number) row[0]).doubleValue() : 0.0;
                    totalManDaysForRatio = row.length > 1 && row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
                }
            } else if (rawResult instanceof Object[]) {
                Object[] row = (Object[]) rawResult;
                if (row.length > 0 && row[0] instanceof Object[]) {
                    Object[] inner = (Object[]) row[0];
                    billableManDays = inner[0] != null ? ((Number) inner[0]).doubleValue() : 0.0;
                    totalManDaysForRatio = inner.length > 1 && inner[1] != null ? ((Number) inner[1]).doubleValue() : 0.0;
                } else {
                    billableManDays = row[0] != null ? ((Number) row[0]).doubleValue() : 0.0;
                    totalManDaysForRatio = row.length > 1 && row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse billable vs total result. Defaulting to 0.", e);
        }

        double billableRatio = totalManDaysForRatio > 0
                ? (billableManDays / totalManDaysForRatio) * 100.0 : 0.0;

        Integer anomalyCount = employeeTimeRepository.countAnomaliesByPeriod(year, month);
        Double anomalyCostImpact = employeeTimeRepository.sumAnomalyCostImpactByPeriod(year, month);

        double budgetVariance = calculateBudgetVariance(year, month, totalManDays);

        return new FinancialStatCards(
                round(totalLaborCost),
                round(billableRatio),
                anomalyCount != null ? anomalyCount : 0,
                anomalyCostImpact != null ? round(anomalyCostImpact) : 0.0,
                round(budgetVariance)
        );
    }

    private double calculateBudgetVariance(Integer year, Integer month, double actualManDays) {
        return actualManDays * 0.0; 
    }

    private List<CostByCompany> buildCostByCompany(LocalDate monthStart, LocalDate monthEnd) {
        List<Object[]> results = employeeTimeRepository.sumManDaysByCompany(monthStart, monthEnd);
        List<CostByCompany> list = new ArrayList<>();
        if (results == null) return list;
        for (Object[] row : results) {
            String companyName = (String) row[0];
            double manDays = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            list.add(new CostByCompany(companyName, round(manDays * DEFAULT_DAY_RATE)));
        }
        return list;
    }

    private List<CostByOu> buildCostByOu(LocalDate monthStart, LocalDate monthEnd) {
        List<Object[]> results = employeeTimeRepository.sumManDaysByOu(monthStart, monthEnd);
        List<CostByOu> list = new ArrayList<>();
        if (results == null) return list;
        
        double totalCost = results.stream()
                .mapToDouble(row -> row[1] != null ? ((Number) row[1]).doubleValue() * DEFAULT_DAY_RATE : 0.0)
                .sum();

        for (Object[] row : results) {
            String ouName = (String) row[0];
            double manDays = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            double cost = manDays * DEFAULT_DAY_RATE;
            double pct = totalCost > 0 ? (cost / totalCost) * 100.0 : 0.0;
            list.add(new CostByOu(ouName, round(cost), round(pct)));
        }
        return list;
    }

    private List<ActivityNatureSplit> buildActivityNatureSplit(LocalDate monthStart, LocalDate monthEnd) {
        List<Object[]> results = employeeTimeRepository.sumManDaysByActivityNature(monthStart, monthEnd);
        List<ActivityNatureSplit> list = new ArrayList<>();
        if (results == null) return list;

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
            double manDays = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            String color = colorMap.getOrDefault(nature, "#9E9E9E");
            list.add(new ActivityNatureSplit(nature, round(manDays), color));
        }
        return list;
    }

    private List<CostTrendPoint> buildCostTrend(Integer year, Integer month) {
        List<CostTrendPoint> trend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            int m = month - i;
            int y = year;
            while (m <= 0) { m += 12; y--; }
            while (m > 12) { m -= 12; y++; }

            Double manDays = employeeTimeRepository.sumTotalManDaysByPeriod(y, m);
            if (manDays == null) manDays = 0.0;
            double cost = manDays * DEFAULT_DAY_RATE;

            String periodLabel = String.format("%s %d",
                    java.time.Month.of(m).toString().substring(0, 3), y);
            trend.add(new CostTrendPoint(periodLabel, round(cost)));
        }
        return trend;
    }

    private List<AccountingCodeCost> buildTopAccountingCodes(LocalDate monthStart, LocalDate monthEnd) {
        List<Object[]> results = employeeTimeRepository.sumManDaysByAccountingCode(monthStart, monthEnd);
        List<AccountingCodeCost> list = new ArrayList<>();
        if (results == null) return list;
        
        for (Object[] row : results) {
            String accCode = (String) row[0];
            String companyName = (String) row[1];
            String ouName = row[2] != null ? (String) row[2] : null;
            String nature = (String) row[3];
            double manDays = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            double cost = manDays * DEFAULT_DAY_RATE;
            list.add(new AccountingCodeCost(accCode, companyName, ouName, nature,
                    round(manDays), round(cost)));
        }
        return list;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}