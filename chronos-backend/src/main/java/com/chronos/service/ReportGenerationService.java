package com.chronos.service;

import com.chronos.dto.internal.*;
import com.chronos.entity.*;
import com.chronos.exception.ChronosException;
import com.chronos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core report generation orchestrator.
 *
 * For each CompanyMember active during the selected MonthPeriod:
 *
 *   Step 1: Check OrganizationalAssignment
 *     → Has assignments → write assignment lines → NEXT EMPLOYEE
 *
 *   Step 2: Fetch timesheets (man_day grouped by AccountingCode)
 *     Branch A — empty (no timesheets):
 *       → Resolve defaults (OU, Product, ActivityNature)
 *       → All found  → Report line with NO_TS_ prefix
 *       → Any missing → Anomaly line
 *
 *     Branch B — single HOLIDAYS entry:
 *       → Calculate capacity (working days from CountryCalendar)
 *       → holidayDays >= capacity → full-holiday month
 *           → Resolve defaults → Report line (AccountingCode = NA)
 *       → holidayDays < capacity → partial holiday
 *           → Resolve defaults → Report line with NO_TS_ prefix
 *           → Any defaults missing → Anomaly line
 *
 *     Branch C — has real timesheets:
 *       → Calculate ratio per AccountingCode (manDays / totalManDays)
 *       → Write one report line per AccountingCode (exclude HOLIDAYS entries)
 *       → If totalManDays < capacity → add NO_TS_ gap line
 *           → Defaults required for gap line; if missing → Anomaly line
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportGenerationService {

    private static final String PREFIX_NO_TS         = "NO_TS_";
    private static final String ACC_CODE_NA           = "NA";
    private static final String HOLIDAYS_NATURE       = "HOLIDAYS";

    private final MonthPeriodRepository               monthPeriodRepository;
    private final CompanyMemberRepository             companyMemberRepository;
    private final OrganizationalAssignmentRepository  orgAssignmentRepository;
    private final EmployeeRepository                  employeeRepository;
    private final AccountingCodeRepository            accountingCodeRepository;
    private final ExcludedOrganizationalUnitRepository excludedOuRepository;
    private final EmployeeTimeQueryService            employeeTimeQueryService;
    private final EmployeeCapacityService             capacityService;

    // ── Public API ────────────────────────────────────────────────────────────

    public ReportResult generateReport(Integer year, Integer month) {
        MonthPeriod period = monthPeriodRepository.findByYearAndMonth(year, month)
                .orElseThrow(() -> new ChronosException(
                        "MonthPeriod not found for " + year + "/" + month +
                        ". Run the Python cleaner first to import data for this period."));

        log.info("Starting report generation for {}/{} (period id={})",
                year, month, period.getId());

        List<CompanyMemberSearchRange> activeMembers =
                companyMemberRepository.findActiveWithSearchRange(
                        period.getStartDate(), period.getEndDate());

        log.info("Found {} active CompanyMembers for period {}/{}",
                activeMembers.size(), year, month);

        List<ReportLine>  reportLines  = new ArrayList<>();
        List<AnomalyLine> anomalyLines = new ArrayList<>();

        for (CompanyMemberSearchRange member : activeMembers) {
            processEmployee(member, period, reportLines, anomalyLines);
        }

        log.info("Report complete — {} report lines, {} anomaly lines",
                reportLines.size(), anomalyLines.size());

        return new ReportResult(reportLines, anomalyLines);
    }

    // ── Per-employee processing ───────────────────────────────────────────────

    private void processEmployee(
            CompanyMemberSearchRange member,
            MonthPeriod period,
            List<ReportLine>  reportLines,
            List<AnomalyLine> anomalyLines) {

        Long employeeId  = member.getEmployeeId();
        Long memberId    = member.getCompanyMemberId();
        LocalDate start  = member.getSearchStart();
        LocalDate end    = member.getSearchEnd();

        Employee employee = employeeRepository.findById(employeeId)
                .orElse(null);
        if (employee == null) {
            log.warn("Employee id={} not found — skipping", employeeId);
            return;
        }

        log.debug("Processing employee {} {} (id={})",
                employee.getFirstName(), employee.getLastName(), employeeId);

        // ── Step 1: OrganizationalAssignment ─────────────────────────────────
        List<OrganizationalAssignment> assignments =
                orgAssignmentRepository.findByEmployeeId(employeeId);

        if (!assignments.isEmpty()) {
            reportLines.addAll(
                    buildAssignmentLines(assignments, employee, member, period));
            return;
        }

        // ── Step 2: Fetch timesheets ──────────────────────────────────────────
        var timesheetGroups = employeeTimeQueryService.getTimesheetGroups(employeeId, start, end);

        if (timesheetGroups.isEmpty()) {
            // Branch A — no timesheets
            handleNoTimesheets(employee, member, period, reportLines, anomalyLines);

        } else if (employeeTimeQueryService.isHolidaysOnly(timesheetGroups)) {
            // Branch B — holidays only
            double holidayDays = timesheetGroups.get(0).getTotalManDays();
            handleHolidaysOnly(employee, member, period, holidayDays, reportLines, anomalyLines);

        } else {
            // Branch C — has real timesheets
            handleHasTimesheets(employee, member, period, timesheetGroups, reportLines, anomalyLines);
        }
    }

    // ── Branch handlers ───────────────────────────────────────────────────────

    /**
     * Step 1 path — employee has an OrganizationalAssignment.
     * One report line per assignment row, scaled by allocationPercentage.
     */
    private List<ReportLine> buildAssignmentLines(
            List<OrganizationalAssignment> assignments,
            Employee employee,
            CompanyMemberSearchRange member,
            MonthPeriod period) {

        int capacity = capacityService.getCapacity(
                member.getCompanyMemberId(), member.getSearchStart(), member.getSearchEnd());

        List<ReportLine> lines = new ArrayList<>();
        for (OrganizationalAssignment oa : assignments) {
            double manDays = capacity * (oa.getAllocationPercentage() / 100.0);
            double ratio   = oa.getAllocationPercentage() / 100.0;

            String ouName     = oa.getOrganizationalUnit().getName();
            String parentName = oa.getOrganizationalUnit().getParent() != null
                    ? oa.getOrganizationalUnit().getParent().getName() : null;

            lines.add(ReportLine.builder()
                    .employeeIdentifier(employee.getIdentifier())
                    .firstName(employee.getFirstName())
                    .lastName(employee.getLastName())
                    .registrationNumber(member.getRegistrationNumber())
                    .companyName(resolveCompanyName(member.getCompanyId()))
                    .ouName(ouName)
                    .parentOuName(parentName)
                    .productName(oa.getProduct() != null ? oa.getProduct().getName() : null)
                    .activityNatureName(oa.getAccountingCode().getActivityNature().getName())
                    .accountingCodeIdentifier(oa.getAccountingCode().getOperationalIdentifier())
                    .manDays(round(manDays))
                    .ratio(round(ratio))
                    .prefix("")
                    .year(period.getYear())
                    .month(period.getMonth())
                    .build());
        }
        return lines;
    }

    /**
     * Branch A — no timesheets booked.
     * Look up default OU/Product/ActivityNature.
     * If all found → report line with NO_TS_ prefix.
     * If any missing → anomaly line.
     */
    private void handleNoTimesheets(
            Employee employee,
            CompanyMemberSearchRange member,
            MonthPeriod period,
            List<ReportLine>  reportLines,
            List<AnomalyLine> anomalyLines) {

        int capacity = capacityService.getCapacity(
                member.getCompanyMemberId(), member.getSearchStart(), member.getSearchEnd());

        Optional<DefaultEmployeeData> defaults = employeeTimeQueryService.resolveDefaults(
                employee.getId(), member.getSearchStart(), member.getSearchEnd());

        if (defaults.isEmpty()) {
            anomalyLines.add(buildAnomaly(employee, member, period,
                    "OrganizationalUnit / ActivityNature",
                    "No timesheets and cannot resolve default OU or ActivityNature"));
            return;
        }

        DefaultEmployeeData d = defaults.get();
        reportLines.add(ReportLine.builder()
                .employeeIdentifier(employee.getIdentifier())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .registrationNumber(member.getRegistrationNumber())
                .companyName(resolveCompanyName(member.getCompanyId()))
                .ouName(d.getOuName())
                .parentOuName(d.getParentOuName())
                .productName(d.getProductName())
                .activityNatureName(d.getActivityNatureName())
                .accountingCodeIdentifier(d.getAccountingCodeIdentifier())
                .manDays((double) capacity)
                .ratio(1.0)
                .prefix(PREFIX_NO_TS)
                .year(period.getYear())
                .month(period.getMonth())
                .build());
    }

    /**
     * Branch B — only HOLIDAYS entries booked.
     *
     * Sub-case B1 — holidayDays >= capacity (full holiday month):
     *   AccountingCode set to "NA". No NO_TS_ prefix.
     *
     * Sub-case B2 — holidayDays < capacity (partial holiday month):
     *   Write a NO_TS_ line for the remaining unbooked days.
     */
    private void handleHolidaysOnly(
            Employee employee,
            CompanyMemberSearchRange member,
            MonthPeriod period,
            double holidayDays,
            List<ReportLine>  reportLines,
            List<AnomalyLine> anomalyLines) {

        int capacity = capacityService.getCapacity(
                member.getCompanyMemberId(), member.getSearchStart(), member.getSearchEnd());

        Optional<DefaultEmployeeData> defaults = employeeTimeQueryService.resolveDefaults(
                employee.getId(), member.getSearchStart(), member.getSearchEnd());

        if (defaults.isEmpty()) {
            anomalyLines.add(buildAnomaly(employee, member, period,
                    "OrganizationalUnit / ActivityNature",
                    "Holidays-only month but cannot resolve default OU or ActivityNature"));
            return;
        }

        DefaultEmployeeData d = defaults.get();
        boolean isFullHolidayMonth = capacityService.isFullyBooked(holidayDays, capacity);

        if (isFullHolidayMonth) {
            // B1 — full holiday month: AccountingCode = NA
            reportLines.add(ReportLine.builder()
                    .employeeIdentifier(employee.getIdentifier())
                    .firstName(employee.getFirstName())
                    .lastName(employee.getLastName())
                    .registrationNumber(member.getRegistrationNumber())
                    .companyName(resolveCompanyName(member.getCompanyId()))
                    .ouName(d.getOuName())
                    .parentOuName(d.getParentOuName())
                    .productName(d.getProductName())
                    .activityNatureName(HOLIDAYS_NATURE)
                    .accountingCodeIdentifier(ACC_CODE_NA)
                    .manDays(holidayDays)
                    .ratio(1.0)
                    .prefix("")
                    .year(period.getYear())
                    .month(period.getMonth())
                    .build());
        } else {
            // B2 — partial holiday month: write NO_TS_ line for gap
            double gapDays = capacityService.getGapDays(holidayDays, capacity);
            reportLines.add(ReportLine.builder()
                    .employeeIdentifier(employee.getIdentifier())
                    .firstName(employee.getFirstName())
                    .lastName(employee.getLastName())
                    .registrationNumber(member.getRegistrationNumber())
                    .companyName(resolveCompanyName(member.getCompanyId()))
                    .ouName(d.getOuName())
                    .parentOuName(d.getParentOuName())
                    .productName(d.getProductName())
                    .activityNatureName(d.getActivityNatureName())
                    .accountingCodeIdentifier(d.getAccountingCodeIdentifier())
                    .manDays(round(gapDays))
                    .ratio(round(gapDays / capacity))
                    .prefix(PREFIX_NO_TS)
                    .year(period.getYear())
                    .month(period.getMonth())
                    .build());
        }
    }

    /**
     * Branch C — employee has real timesheets.
     *
     * For each non-HOLIDAYS accounting code:
     *   ratio    = manDays / totalNonHolidayDays
     *   manDays  = ratio * capacity
     *   Write one report line.
     *
     * If totalNonHolidayDays < capacity (gap exists):
     *   Write an additional NO_TS_ line for the gap.
     *   If defaults cannot be resolved → anomaly line for the gap.
     */
    private void handleHasTimesheets(
            Employee employee,
            CompanyMemberSearchRange member,
            MonthPeriod period,
            List<TimesheetSummary> groups,
            List<ReportLine>  reportLines,
            List<AnomalyLine> anomalyLines) {

        int capacity = capacityService.getCapacity(
                member.getCompanyMemberId(), member.getSearchStart(), member.getSearchEnd());

        // Sum only non-HOLIDAYS entries for ratio denominator
        double totalNonHolidayDays = groups.stream()
                .filter(g -> !HOLIDAYS_NATURE.equalsIgnoreCase(
                        employeeTimeQueryService.getTimesheetGroups(
                                employee.getId(), member.getSearchStart(), member.getSearchEnd())
                                .stream().findFirst().map(x -> "").orElse("")))
                .mapToDouble(TimesheetSummary::getTotalManDays)
                .sum();

        // Simpler and more efficient: filter holidays via activity nature name lookup
        double holidayDays = 0.0;
        double realDays    = 0.0;

        for (TimesheetSummary group : groups) {
            String nature = employeeTimeRepository_findNature(group.getAccountingCodeId());
            if (HOLIDAYS_NATURE.equalsIgnoreCase(nature)) {
                holidayDays += group.getTotalManDays();
            } else {
                realDays += group.getTotalManDays();
            }
        }

        double totalDays = realDays + holidayDays;

        // Write one line per non-holiday accounting code
        for (TimesheetSummary group : groups) {
            String nature = employeeTimeRepository_findNature(group.getAccountingCodeId());
            if (HOLIDAYS_NATURE.equalsIgnoreCase(nature)) continue;

            AccountingCode ac = accountingCodeRepository
                    .findByIdWithDetails(group.getAccountingCodeId()).orElse(null);
            if (ac == null) {
                log.warn("AccountingCode id={} not found — skipping timesheet group",
                        group.getAccountingCodeId());
                continue;
            }

            double ratio   = totalDays > 0 ? group.getTotalManDays() / totalDays : 0.0;
            double manDays = ratio * capacity;

            reportLines.add(ReportLine.builder()
                    .employeeIdentifier(employee.getIdentifier())
                    .firstName(employee.getFirstName())
                    .lastName(employee.getLastName())
                    .registrationNumber(member.getRegistrationNumber())
                    .companyName(resolveCompanyName(member.getCompanyId()))
                    .ouName(ac.getOrganizationalUnit() != null
                            ? ac.getOrganizationalUnit().getName() : null)
                    .parentOuName(ac.getOrganizationalUnit() != null
                            && ac.getOrganizationalUnit().getParent() != null
                            ? ac.getOrganizationalUnit().getParent().getName() : null)
                    .productName(ac.getProduct() != null ? ac.getProduct().getName() : null)
                    .activityNatureName(ac.getActivityNature().getName())
                    .accountingCodeIdentifier(ac.getOperationalIdentifier())
                    .manDays(round(manDays))
                    .ratio(round(ratio))
                    .prefix("")
                    .year(period.getYear())
                    .month(period.getMonth())
                    .build());
        }

        // Check for gap — if total booked days < capacity, add NO_TS_ line
        double gapDays = capacityService.getGapDays(totalDays, capacity);
        if (gapDays > 0.01) {
            Optional<DefaultEmployeeData> defaults = employeeTimeQueryService.resolveDefaults(
                    employee.getId(), member.getSearchStart(), member.getSearchEnd());

            if (defaults.isEmpty()) {
                anomalyLines.add(buildAnomaly(employee, member, period,
                        "OrganizationalUnit / ActivityNature",
                        "Gap of " + round(gapDays) + " days but cannot resolve defaults"));
            } else {
                DefaultEmployeeData d = defaults.get();
                double gapRatio = capacity > 0 ? gapDays / capacity : 0.0;
                reportLines.add(ReportLine.builder()
                        .employeeIdentifier(employee.getIdentifier())
                        .firstName(employee.getFirstName())
                        .lastName(employee.getLastName())
                        .registrationNumber(member.getRegistrationNumber())
                        .companyName(resolveCompanyName(member.getCompanyId()))
                        .ouName(d.getOuName())
                        .parentOuName(d.getParentOuName())
                        .productName(d.getProductName())
                        .activityNatureName(d.getActivityNatureName())
                        .accountingCodeIdentifier(d.getAccountingCodeIdentifier())
                        .manDays(round(gapDays))
                        .ratio(round(gapRatio))
                        .prefix(PREFIX_NO_TS)
                        .year(period.getYear())
                        .month(period.getMonth())
                        .build());
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private final EmployeeTimeRepository employeeTimeRepository;
    private final CompanyRepository companyRepository;

    private String employeeTimeRepository_findNature(Long accountingCodeId) {
        return employeeTimeRepository.findActivityNatureByAccountingCodeId(accountingCodeId);
    }

    private String resolveCompanyName(Long companyId) {
        return companyRepository.findById(companyId)
                .map(c -> c.getName())
                .orElse("");
    }

    private AnomalyLine buildAnomaly(
            Employee employee,
            CompanyMemberSearchRange member,
            MonthPeriod period,
            String missingField,
            String issue) {
        return AnomalyLine.builder()
                .employeeIdentifier(employee.getIdentifier())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .registrationNumber(member.getRegistrationNumber())
                .companyName(resolveCompanyName(member.getCompanyId()))
                .missingField(missingField)
                .issue(issue)
                .year(period.getYear())
                .month(period.getMonth())
                .build();
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
