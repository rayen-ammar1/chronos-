package com.chronos.repository;

import com.chronos.dto.internal.TimesheetSummary;
import com.chronos.entity.EmployeeTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EmployeeTimeRepository extends JpaRepository<EmployeeTime, Long> {

    @Query(value =
        "SELECT " +
        "    et.accounting_code_id  AS accountingCodeId, " +
        "    SUM(et.man_day)        AS totalManDays, " +
        "    COUNT(*)               AS entryCount " +
        "FROM employee_time et " +
        "WHERE et.employee_id = :employeeId " +
        "  AND et.date BETWEEN :searchStart AND :searchEnd " +
        "GROUP BY et.accounting_code_id",
        nativeQuery = true)
    List<TimesheetSummary> sumByAccountingCode(
            @Param("employeeId")  Long      employeeId,
            @Param("searchStart") LocalDate searchStart,
            @Param("searchEnd")   LocalDate searchEnd
    );

    @Query(value =
        "SELECT UPPER(an.name) " +
        "FROM accounting_code ac " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "WHERE ac.id = :accountingCodeId " +
        "LIMIT 1",
        nativeQuery = true)
    String findActivityNatureByAccountingCodeId(
            @Param("accountingCodeId") Long accountingCodeId
    );

    // ── Financial Officer dashboard aggregations ─────────────────────────────

    @Query(value =
        "SELECT " +
        "    co.name                                                       AS companyName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN employee e           ON e.id = et.employee_id " +
        "JOIN company_member cm      ON cm.employee_id = e.id " +
        "JOIN company co             ON co.id = cm.company_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd " +
        "  AND cm.start_date <= :monthEnd " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= :monthStart) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= :monthEnd " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= :monthStart) " +
        "  ) " +
        "GROUP BY co.name " +
        "ORDER BY totalManDays DESC " +
        "LIMIT 5",
        nativeQuery = true)
    List<Object[]> sumManDaysByCompany(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    // 🚀 FIX 1: Changed oum join to use et.date instead of monthStart/monthEnd
    @Query(value =
        "SELECT " +
        "    ou.name                                                       AS ouName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN employee e           ON e.id = et.employee_id " +
        "JOIN organizational_unit_member oum ON oum.employee_id = e.id " +
        "  AND oum.start_date <= et.date " +
        "  AND (oum.end_date IS NULL OR oum.end_date >= et.date) " +
        "JOIN organizational_unit ou ON ou.id = oum.organizational_unit_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    WHERE eou.organizational_unit_id = ou.id " +
        "  ) " +
        "GROUP BY ou.name " +
        "ORDER BY totalManDays DESC",
        nativeQuery = true)
    List<Object[]> sumManDaysByOu(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT " +
        "    UPPER(an.name)                                                AS natureName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd " +
        "  AND UPPER(an.name) != 'HOLIDAYS' " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    JOIN employee e2 ON e2.id = et.employee_id " +
        "    WHERE oum.employee_id = e2.id " +
        "      AND oum.start_date <= :monthEnd " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= :monthStart) " +
        "  ) " +
        "GROUP BY UPPER(an.name) " +
        "ORDER BY totalManDays DESC",
        nativeQuery = true)
    List<Object[]> sumManDaysByActivityNature(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT " +
        "    ac.operational_identifier                                     AS accCodeIdentifier, " +
        "    co.name                                                       AS companyName, " +
        "    ou.name                                                       AS ouName, " +
        "    UPPER(an.name)                                                AS natureName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "JOIN employee e           ON e.id = et.employee_id " +
        "JOIN company_member cm      ON cm.employee_id = e.id " +
        "JOIN company co             ON co.id = cm.company_id " +
        "LEFT JOIN organizational_unit_member oum ON oum.employee_id = e.id " +
        "LEFT JOIN organizational_unit ou ON ou.id = oum.organizational_unit_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd " +
        "  AND UPPER(an.name) != 'HOLIDAYS' " +
        "  AND cm.start_date <= :monthEnd " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= :monthStart) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    WHERE eou.organizational_unit_id = ou.id " +
        "  ) " +
        "GROUP BY ac.operational_identifier, co.name, ou.name, UPPER(an.name) " +
        "ORDER BY totalManDays DESC " +
        "LIMIT 10",
        nativeQuery = true)
    List<Object[]> sumManDaysByAccountingCode(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT " +
        "    SUM(CASE WHEN UPPER(an.name) IN ('REGIE', 'FORFAIT') THEN et.man_day ELSE 0 END) AS billableManDays, " +
        "    SUM(et.man_day)                                                                 AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "JOIN employee e           ON e.id = et.employee_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= :monthEnd " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= :monthStart) " +
        "  )",
        nativeQuery = true)
    Object[] sumBillableVsTotal(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT SUM(et.man_day) " +
        "FROM employee_time et " +
        "JOIN employee e ON e.id = et.employee_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= :monthEnd " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= :monthStart) " +
        "  )",
        nativeQuery = true)
    Double sumTotalManDays(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT SUM(et.man_day) " +
        "FROM employee_time et " +
        "JOIN employee e ON e.id = et.employee_id " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE et.date BETWEEN mp.start_date AND mp.end_date " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= mp.end_date " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= mp.start_date) " +
        "  )",
        nativeQuery = true)
    Double sumTotalManDaysByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    @Query(value =
        "SELECT " +
        "    ac.operational_identifier                                     AS accCodeIdentifier, " +
        "    co.name                                                       AS companyName, " +
        "    ou.name                                                       AS ouName, " +
        "    UPPER(an.name)                                                AS natureName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "JOIN employee e           ON e.id = et.employee_id " +
        "JOIN company_member cm      ON cm.employee_id = e.id " +
        "JOIN company co             ON co.id = cm.company_id " +
        "LEFT JOIN organizational_unit_member oum ON oum.employee_id = e.id " +
        "LEFT JOIN organizational_unit ou ON ou.id = oum.organizational_unit_id " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE et.date BETWEEN mp.start_date AND mp.end_date " +
        "  AND UPPER(an.name) != 'HOLIDAYS' " +
        "  AND cm.start_date <= mp.end_date " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= mp.start_date) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    WHERE eou.organizational_unit_id = ou.id " +
        "  ) " +
        "GROUP BY ac.operational_identifier, co.name, ou.name, UPPER(an.name) " +
        "ORDER BY totalManDays DESC " +
        "LIMIT 10",
        nativeQuery = true)
    List<Object[]> sumManDaysByAccountingCodeByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    @Query(value =
        "SELECT " +
        "    SUM(CASE WHEN UPPER(an.name) IN ('REGIE', 'FORFAIT') THEN et.man_day ELSE 0 END) AS billableManDays, " +
        "    SUM(et.man_day)                                                                 AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "JOIN employee e           ON e.id = et.employee_id " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE et.date BETWEEN mp.start_date AND mp.end_date " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= mp.end_date " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= mp.start_date) " +
        "  )",
        nativeQuery = true)
    Object[] sumBillableVsTotalByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    @Query(value =
        "SELECT " +
        "    co.name                                                       AS companyName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN employee e           ON e.id = et.employee_id " +
        "JOIN company_member cm      ON cm.employee_id = e.id " +
        "JOIN company co             ON co.id = cm.company_id " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE et.date BETWEEN mp.start_date AND mp.end_date " +
        "  AND cm.start_date <= mp.end_date " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= mp.start_date) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= mp.end_date " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= mp.start_date) " +
        "  ) " +
        "GROUP BY co.name " +
        "ORDER BY totalManDays DESC " +
        "LIMIT 5",
        nativeQuery = true)
    List<Object[]> sumManDaysByCompanyByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    // 🚀 FIX 2: Changed oum join to use et.date instead of mp.start_date/mp.end_date
    @Query(value =
        "SELECT " +
        "    ou.name                                                       AS ouName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN employee e           ON e.id = et.employee_id " +
        "JOIN organizational_unit_member oum ON oum.employee_id = e.id " +
        "  AND oum.start_date <= et.date " +
        "  AND (oum.end_date IS NULL OR oum.end_date >= et.date) " +
        "JOIN organizational_unit ou ON ou.id = oum.organizational_unit_id " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE et.date BETWEEN mp.start_date AND mp.end_date " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    WHERE eou.organizational_unit_id = ou.id " +
        "  ) " +
        "GROUP BY ou.name " +
        "ORDER BY totalManDays DESC",
        nativeQuery = true)
    List<Object[]> sumManDaysByOuByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    @Query(value =
        "SELECT " +
        "    UPPER(an.name)                                                AS natureName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "JOIN employee e           ON e.id = et.employee_id " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE et.date BETWEEN mp.start_date AND mp.end_date " +
        "  AND UPPER(an.name) != 'HOLIDAYS' " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= mp.end_date " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= mp.start_date) " +
        "  ) " +
        "GROUP BY UPPER(an.name) " +
        "ORDER BY totalManDays DESC",
        nativeQuery = true)
    List<Object[]> sumManDaysByActivityNatureByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    @Query(value =
        "SELECT COUNT(DISTINCT cm.employee_id) " +
        "FROM company_member cm " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE cm.start_date <= mp.end_date " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= mp.start_date) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = cm.employee_id " +
        "      AND oum.start_date <= mp.end_date " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= mp.start_date) " +
        "  ) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM employee_time et " +
        "    WHERE et.employee_id = cm.employee_id " +
        "      AND et.date BETWEEN mp.start_date AND mp.end_date " +
        "  ) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM organizational_assignment oa " +
        "    WHERE oa.employee_id = cm.employee_id " +
        "  )",
        nativeQuery = true)
    Integer countAnomaliesByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    @Query(value =
        "SELECT COALESCE(SUM(0.0), 0.0) " +
        "FROM company_member cm " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE cm.start_date <= mp.end_date " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= mp.start_date) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = cm.employee_id " +
        "      AND oum.start_date <= mp.end_date " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= mp.start_date) " +
        "  ) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM employee_time et " +
        "    WHERE et.employee_id = cm.employee_id " +
        "      AND et.date BETWEEN mp.start_date AND mp.end_date " +
        "  ) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM organizational_assignment oa " +
        "    WHERE oa.employee_id = cm.employee_id " +
        "  )",
        nativeQuery = true)
    Double sumAnomalyCostImpactByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );
}