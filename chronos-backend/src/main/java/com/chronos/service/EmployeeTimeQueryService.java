package com.chronos.service;

import com.chronos.dto.internal.DefaultEmployeeData;
import com.chronos.dto.internal.TimesheetSummary;
import com.chronos.entity.EmployeeByActivityNature;
import com.chronos.entity.EmployeeByProduct;
import com.chronos.entity.OrganizationalUnitMember;
import com.chronos.repository.EmployeeByActivityNatureRepository;
import com.chronos.repository.EmployeeByProductRepository;
import com.chronos.repository.EmployeeTimeRepository;
import com.chronos.repository.OrganizationalUnitMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeTimeQueryService {

    private static final String HOLIDAYS_NATURE = "HOLIDAYS";

    private final EmployeeTimeRepository              employeeTimeRepository;
    private final OrganizationalUnitMemberRepository  ouMemberRepository;
    private final EmployeeByProductRepository         productRepository;
    private final EmployeeByActivityNatureRepository  activityNatureRepository;

    /**
     * Fetch timesheets grouped by accounting code for the search range.
     * Returns empty list if no timesheets exist — this drives the no-timesheets branch.
     */
    public List<TimesheetSummary> getTimesheetGroups(
            Long employeeId, LocalDate searchStart, LocalDate searchEnd) {
        return employeeTimeRepository.sumByAccountingCode(employeeId, searchStart, searchEnd);
    }

    /**
     * Returns true if the only timesheet group is a HOLIDAYS accounting code.
     * A group qualifies as holidays-only when:
     *   - There is exactly one group in the list
     *   - Its accounting code's ActivityNature name equals "HOLIDAYS"
     */
    public boolean isHolidaysOnly(List<TimesheetSummary> groups) {
        if (groups.size() != 1) return false;
        String natureName = employeeTimeRepository
                .findActivityNatureByAccountingCodeId(groups.get(0).getAccountingCodeId());
        return HOLIDAYS_NATURE.equalsIgnoreCase(natureName);
    }

    /**
     * Resolves the default cost-allocation context for an employee.
     * Used in the no-timesheets and holidays-only fallback paths.
     *
     * All three of OU, product, and activityNature must resolve successfully
     * for a report line to be written. If any is missing, the caller should
     * write an anomaly line instead.
     *
     * Note: product is OPTIONAL — an employee without a product assignment
     * returns a DefaultEmployeeData with productName = null.
     * This is NOT an anomaly — some employees genuinely have no product.
     */
    public Optional<DefaultEmployeeData> resolveDefaults(
            Long employeeId, LocalDate searchStart, LocalDate searchEnd) {

        // 1. Resolve OU (required)
        Optional<OrganizationalUnitMember> ouMember =
                ouMemberRepository.findActiveByEmployeeId(employeeId, searchStart, searchEnd);
        if (ouMember.isEmpty()) {
            log.warn("No active OrganizationalUnitMember for employeeId={} [{} – {}]",
                    employeeId, searchStart, searchEnd);
            return Optional.empty();
        }
        String ouName     = ouMember.get().getOrganizationalUnit().getName();
        String parentName = ouMember.get().getOrganizationalUnit().getParent() != null
                ? ouMember.get().getOrganizationalUnit().getParent().getName()
                : null;

        // 2. Resolve ActivityNature (required)
        Optional<EmployeeByActivityNature> nature =
                activityNatureRepository.findActiveByEmployeeId(employeeId, searchStart, searchEnd);
        if (nature.isEmpty()) {
            log.warn("No active EmployeeByActivityNature for employeeId={} [{} – {}]",
                    employeeId, searchStart, searchEnd);
            return Optional.empty();
        }
        String natureName = nature.get().getActivityNature().getName();

        // 3. Resolve Product (optional — null is valid)
        Optional<EmployeeByProduct> product =
                productRepository.findActiveByEmployeeId(employeeId, searchStart, searchEnd);
        String productName = product.map(p -> p.getProduct().getName()).orElse(null);

        // 4. Build accounting code identifier for output line
        // Convention: NO_TS_ prefix is added by the caller — here we just
        // return the raw OU + parent context for line construction.
        String accCodeIdentifier = buildDefaultAccCodeIdentifier(parentName, ouName);

        return Optional.of(DefaultEmployeeData.builder()
                .ouName(ouName)
                .parentOuName(parentName)
                .productName(productName)
                .activityNatureName(natureName)
                .accountingCodeIdentifier(accCodeIdentifier)
                .build());
    }

    /**
     * Builds the accounting code identifier for default (no-timesheet) lines.
     * Convention from spec: parentOU_childOU.
     * If no parent exists, uses just the OU name.
     */
    private String buildDefaultAccCodeIdentifier(String parentName, String ouName) {
        if (parentName != null && !parentName.isBlank()) {
            return parentName + "_" + ouName;
        }
        return ouName;
    }
}
