package com.chronos.service;

import com.chronos.repository.CountryCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeCapacityService {

    private final CountryCalendarRepository countryCalendarRepository;

    /**
     * Returns the number of working days for a company member during
     * their resolved search range (searchStart to searchEnd).
     *
     * This is the employee's CAPACITY for the period. It is compared
     * against the sum of man_day values from their timesheets to determine:
     *   - Whether a holidays-only month covers the full capacity
     *     (totalHolidayDays >= capacity → full-holiday month → AccountingCode = NA)
     *   - Whether a timesheet-based allocation is complete
     *     (totalTimesheetDays < capacity → gap exists → add NO_TS_ line)
     *
     * Returns 0 if no CountryCalendar data exists for this company/period.
     * The service logs a warning so missing calendar data is visible.
     */
    public int getCapacity(Long companyMemberId, LocalDate searchStart, LocalDate searchEnd) {
        Integer count = countryCalendarRepository.countWorkingDays(
                companyMemberId, searchStart, searchEnd
        );
        if (count == null || count == 0) {
            log.warn(
                "No CountryCalendar data found for companyMemberId={} between {} and {}. " +
                "Capacity defaults to 0 — check that calendar data has been loaded.",
                companyMemberId, searchStart, searchEnd
            );
            return 0;
        }
        return count;
    }

    /**
     * True if the employee's total timesheet days equals or exceeds capacity.
     * A tolerance of 0.01 days accounts for floating-point precision.
     */
    public boolean isFullyBooked(double totalManDays, int capacity) {
        return totalManDays >= (capacity - 0.01);
    }

    /**
     * Remaining unbooked days — used to generate the NO_TS_ gap line.
     * Returns 0 if already fully booked.
     */
    public double getGapDays(double totalManDays, int capacity) {
        double gap = capacity - totalManDays;
        return Math.max(gap, 0.0);
    }
}
