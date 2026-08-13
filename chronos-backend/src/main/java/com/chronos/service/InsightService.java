package com.chronos.service;

import com.chronos.dto.Insight;
import com.chronos.entity.MonthPeriod;
import com.chronos.repository.EmployeeByProductRepository;
import com.chronos.repository.EmployeeTimeRepository;
import com.chronos.repository.MonthPeriodRepository;
import com.chronos.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsightService {

    private final MonthPeriodRepository monthPeriodRepository;
    private final EmployeeTimeRepository employeeTimeRepository;
    private final EmployeeByProductRepository employeeByProductRepository;
    private final ReportRepository reportRepository;

    public List<Insight> getInsights(Integer year, Integer month) {
        List<Insight> out = new ArrayList<>();
        MonthPeriod period = monthPeriodRepository.findByYearAndMonth(year, month).orElse(null);
        if (period == null) return out;
        LocalDate start = period.getStartDate();
        LocalDate end = period.getEndDate();

        // ── 1. Billable ratio ────────────────────────────────────────────────
        double[] b = billable(start, end);
        double ratio = b[1] > 0 ? (b[0] / b[1]) * 100.0 : 0.0;
        if (ratio > 0 && ratio < 70)
            out.add(new Insight("WARNING", "Low billable ratio",
                String.format("Billable ratio is %.1f%% (target >= 70%%). Shift capacity toward REGIE/FORFAIT codes.", ratio)));
        else if (ratio >= 70)
            out.add(new Insight("OK", "Healthy billable ratio",
                String.format("%.1f%% of logged man-days are billable.", ratio)));

        // ── 2. Cost concentration ───────────────────────────────────────────
        List<Object[]> ous = employeeTimeRepository.sumManDaysByOu(start, end);
        double totalOu = ous.stream().mapToDouble(r -> num(r[1])).sum();
        if (totalOu > 0 && !ous.isEmpty()) {
            Object[] top = ous.get(0);
            double share = num(top[1]) / totalOu * 100.0;
            if (share > 30)
                out.add(new Insight("WARNING", "Cost concentration risk",
                    String.format("OU '%s' carries %.1f%% of total cost. Consider diversifying staffing.", top[0], share)));
        }

        // ── 3. Month-over-month trend ───────────────────────────────────────
        int pm = month - 1, py = year;
        if (pm == 0) { pm = 12; py--; }
        Double prev = employeeTimeRepository.sumTotalManDaysByPeriod(py, pm);
        Double curr = employeeTimeRepository.sumTotalManDaysByPeriod(year, month);
        if (prev != null && prev > 0 && curr != null) {
            double delta = (curr - prev) / prev * 100.0;
            if (delta > 10)
                out.add(new Insight("WARNING", "Cost spike vs previous month",
                    String.format("Man-days increased %+.1f%% vs %d/%d. Verify the drivers behind the surge.", delta, py, pm)));
            else if (delta < -10)
                out.add(new Insight("INFO", "Activity drop vs previous month",
                    String.format("Man-days decreased %.1f%% vs %d/%d. Check for missing timesheets or idle capacity.", delta, py, pm)));
        }

        // ── 4. Data quality & compliance ────────────────────────────────────
        int missing = reportRepository.missingTimesheets(start, end).size();
        if (missing > 0)
            out.add(new Insight("WARNING", "Missing timesheets",
                missing + " active employees logged no time this period. Send reminders (see Anomalies CSV)."));

        int weekend = reportRepository.weekendWork(start, end).size();
        if (weekend > 0)
            out.add(new Insight("WARNING", "Work on non-working days",
                weekend + " timesheet entries fall on weekends/holidays. Review overtime compliance."));

        int notValidated = reportRepository.notValidated(start, end).size();
        if (notValidated > 0)
            out.add(new Insight("INFO", "Validation backlog",
                notValidated + " timesheets are not validated yet. Ask validators to clear the queue."));

        // ── 5. Product utilization ──────────────────────────────────────────
        Double md = employeeByProductRepository.sumManDaysOnProducts(start, end);
        Integer cap = employeeByProductRepository.sumCapacityForProductEmployees(start, end);
        if (md != null && cap != null && cap > 0) {
            double util = md / cap * 100.0;
            if (util > 100)
                out.add(new Insight("WARNING", "Product over-allocation",
                    String.format("Utilization is %.0f%% (>100%%). Product teams are overbooked - burnout risk.", util)));
            else if (util < 60)
                out.add(new Insight("INFO", "Spare product capacity",
                    String.format("Utilization is %.0f%%. Free capacity available to absorb new product work.", util)));
        }

        if (out.isEmpty())
            out.add(new Insight("OK", "All clear", "No significant anomalies detected for this period."));

        return out;
    }

    private double[] billable(LocalDate s, LocalDate e) {
        Object raw = employeeTimeRepository.sumBillableVsTotal(s, e);
        Object[] row = null;
        if (raw instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Object[] o) row = o;
        else if (raw instanceof Object[] o) row = (o.length > 0 && o[0] instanceof Object[] inner) ? inner : o;
        if (row == null) return new double[]{0, 0};
        return new double[]{num(row[0]), row.length > 1 ? num(row[1]) : 0};
    }

    private double num(Object o) { return o instanceof Number n ? n.doubleValue() : 0.0; }
}