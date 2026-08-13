package com.chronos.service;
import com.chronos.entity.MonthPeriod;
import com.chronos.repository.EmployeeTimeRepository;
import com.chronos.repository.MonthPeriodRepository;
import com.chronos.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final double DAY_RATE = 700.0;

    private final MonthPeriodRepository monthPeriodRepository;
    private final EmployeeTimeRepository employeeTimeRepository;
    private final ReportRepository reportRepository;

    // ── Financial report ─────────────────────────────────────────────────────

    public byte[] buildFinancialReport(Integer year, Integer month) {
        StringBuilder sb = new StringBuilder("\uFEFF"); // Excel-friendly BOM
        sb.append("CHRONOS - FINANCIAL REPORT\n");
        sb.append("Period,").append(year).append("-").append(String.format("%02d", month)).append("\n");
        sb.append("Generated,").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");

        MonthPeriod period = monthPeriodRepository.findByYearAndMonth(year, month).orElse(null);
        if (period == null) {
            sb.append("No data for this period.\n");
            return bytes(sb);
        }
        LocalDate start = period.getStartDate();
        LocalDate end = period.getEndDate();

        double totalManDays = num(employeeTimeRepository.sumTotalManDays(start, end));
        double[] billable = billable(start, end);
        double billableRatio = billable[1] > 0 ? (billable[0] / billable[1]) * 100.0 : 0.0;
        Integer anomalyCount = employeeTimeRepository.countAnomaliesByPeriod(year, month);

        sb.append("KPI,Value\n");
        sb.append("Total Man-Days,").append(fmt(totalManDays)).append("\n");
        sb.append("Total Labor Cost (EUR),").append(fmt(totalManDays * DAY_RATE)).append("\n");
        sb.append("Billable Ratio (%),").append(fmt(billableRatio)).append("\n");
        sb.append("Cost Anomalies,").append(anomalyCount != null ? anomalyCount : 0).append("\n\n");

        sb.append("COST BY COMPANY\nCompany,Cost (EUR)\n");
        for (Object[] r : employeeTimeRepository.sumManDaysByCompany(start, end))
            sb.append(esc(str(r[0]))).append(",").append(fmt(num(r[1]) * DAY_RATE)).append("\n");
        sb.append("\n");

        sb.append("COST BY OU\nOU,Cost (EUR)\n");
        for (Object[] r : employeeTimeRepository.sumManDaysByOu(start, end))
            sb.append(esc(str(r[0]))).append(",").append(fmt(num(r[1]) * DAY_RATE)).append("\n");
        sb.append("\n");

        sb.append("ACTIVITY NATURE SPLIT\nNature,Man-Days\n");
        for (Object[] r : employeeTimeRepository.sumManDaysByActivityNature(start, end))
            sb.append(esc(str(r[0]))).append(",").append(fmt(num(r[1]))).append("\n");
        sb.append("\n");

        sb.append("TOP ACCOUNTING CODES\nCode,Company,OU,Nature,Man-Days,Cost (EUR)\n");
        for (Object[] r : employeeTimeRepository.sumManDaysByAccountingCode(start, end))
            sb.append(esc(str(r[0]))).append(",").append(esc(str(r[1]))).append(",")
              .append(esc(str(r[2]))).append(",").append(esc(str(r[3]))).append(",")
              .append(fmt(num(r[4]))).append(",").append(fmt(num(r[4]) * DAY_RATE)).append("\n");

        return bytes(sb);
    }

    // ── Anomalies report ─────────────────────────────────────────────────────

    public byte[] buildAnomaliesReport(Integer year, Integer month) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("CHRONOS - ANOMALIES REPORT\n");
        sb.append("Period,").append(year).append("-").append(String.format("%02d", month)).append("\n\n");

        MonthPeriod period = monthPeriodRepository.findByYearAndMonth(year, month).orElse(null);
        if (period == null) {
            sb.append("No data for this period.\n");
            return bytes(sb);
        }
        LocalDate start = period.getStartDate();
        LocalDate end = period.getEndDate();

        sb.append("Type,Identifier,First Name,Last Name,Detail,Man-Days,Status\n");

        for (Object[] r : reportRepository.missingTimesheets(start, end))
            sb.append("MISSING_TIMESHEET,").append(esc(str(r[0]))).append(",").append(esc(str(r[1])))
              .append(",").append(esc(str(r[2]))).append(",").append(esc(str(r[3]))).append(",,\n");

        for (Object[] r : reportRepository.weekendWork(start, end))
            sb.append("WEEKEND_WORK,").append(esc(str(r[0]))).append(",").append(esc(str(r[1])))
              .append(",").append(esc(str(r[2]))).append(",").append(esc(str(r[3]))).append(",")
              .append(fmt(num(r[4]))).append(",").append(esc(str(r[5]))).append("\n");

        for (Object[] r : reportRepository.overbooked(start, end))
            sb.append("OVERBOOKED_GT_1_DAY,").append(esc(str(r[0]))).append(",").append(esc(str(r[1])))
              .append(",").append(esc(str(r[2]))).append(",").append(esc(str(r[3]))).append(",")
              .append(fmt(num(r[4]))).append(",").append(esc(str(r[5]))).append("\n");

        for (Object[] r : reportRepository.notValidated(start, end))
            sb.append("NOT_VALIDATED,").append(esc(str(r[0]))).append(",").append(esc(str(r[1])))
              .append(",").append(esc(str(r[2]))).append(",").append(esc(str(r[3]))).append(",")
              .append(fmt(num(r[4]))).append(",").append(esc(str(r[5]))).append("\n");

        return bytes(sb);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private double[] billable(LocalDate s, LocalDate e) {
        Object raw = employeeTimeRepository.sumBillableVsTotal(s, e);
        Object[] row = null;
        if (raw instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Object[] o) row = o;
        else if (raw instanceof Object[] o) row = (o.length > 0 && o[0] instanceof Object[] inner) ? inner : o;
        if (row == null) return new double[]{0, 0};
        return new double[]{num(row[0]), row.length > 1 ? num(row[1]) : 0};
    }

    private double num(Object o) { return o instanceof Number n ? n.doubleValue() : 0.0; }
    private String str(Object o) { return o != null ? o.toString() : ""; }
    private String fmt(double d) { return String.format(Locale.US, "%.2f", d); }
    private byte[] bytes(StringBuilder sb) { return sb.toString().getBytes(StandardCharsets.UTF_8); }

    private String esc(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return (v.contains(",") || v.contains("\"") || v.contains("\n")) ? "\"" + v + "\"" : v;
    }
} 
