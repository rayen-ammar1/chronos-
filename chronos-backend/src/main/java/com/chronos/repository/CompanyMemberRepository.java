package com.chronos.repository;

import com.chronos.dto.internal.CompanyMemberSearchRange;
import com.chronos.entity.CompanyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {

    /**
     * CRITICAL — the main driver of the report loop.
     *
     * Fetches all CompanyMembers active during a MonthPeriod AND resolves
     * their search date ranges in a single round-trip using GREATEST / LEAST.
     *
     * This collapses all 6 scenarios from spec Table 1 into two expressions:
     *   searchStart = GREATEST(cm.start_date, :monthStart)
     *   searchEnd   = LEAST(COALESCE(cm.end_date, :monthEnd), :monthEnd)
     *
     * The overlap condition that determines "active during period":
     *   cm.start_date <= :monthEnd
     *   AND (cm.end_date IS NULL OR cm.end_date >= :monthStart)
     *
     * Every downstream query (timesheets, OU, capacity) uses searchStart
     * and searchEnd — they never recalculate from raw CompanyMember dates.
     *
     * Index used: idx_cm_dates on (start_date, end_date)
     */
    @Query(value =
        "SELECT " +
        "    cm.id                                                   AS companyMemberId, " +
        "    cm.employee_id                                          AS employeeId, " +
        "    cm.company_id                                           AS companyId, " +
        "    cm.registration_number                                  AS registrationNumber, " +
        "    cm.start_date                                           AS startDate, " +
        "    cm.end_date                                             AS endDate, " +
        "    GREATEST(cm.start_date, :monthStart)                   AS searchStart, " +
        "    LEAST(COALESCE(cm.end_date, :monthEnd), :monthEnd)     AS searchEnd " +
        "FROM company_member cm " +
        "WHERE cm.start_date <= :monthEnd " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= :monthStart) " +
        "ORDER BY cm.employee_id",
        nativeQuery = true)
    List<CompanyMemberSearchRange> findActiveWithSearchRange(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );
}
