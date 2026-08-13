package com.chronos.repository;

import com.chronos.entity.CountryCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
public interface CountryCalendarRepository extends JpaRepository<CountryCalendar, Long> {

    /**
     * Counts working days for a CompanyMember's country in a date range.
     * Result is the employee's CAPACITY — compared against sum of man_day
     * to detect incomplete bookings and full-holiday months.
     * Joins through company_member → company to resolve the country.
     * Index used: idx_cc_country_date on (country, date, is_working_day)
     */
    @Query(value =
        "SELECT COUNT(*) " +
        "FROM country_calendar cc " +
        "JOIN company co        ON co.country    = cc.country " +
        "JOIN company_member cm ON cm.company_id = co.id " +
        "WHERE cm.id = :companyMemberId " +
        "  AND cc.date BETWEEN :searchStart AND :searchEnd " +
        "  AND cc.is_working_day = TRUE",
        nativeQuery = true)
    Integer countWorkingDays(
            @Param("companyMemberId") Long      companyMemberId,
            @Param("searchStart")     LocalDate searchStart,
            @Param("searchEnd")       LocalDate searchEnd
    );
}
