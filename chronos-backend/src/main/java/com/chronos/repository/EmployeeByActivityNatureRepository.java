package com.chronos.repository;

import com.chronos.entity.EmployeeByActivityNature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeByActivityNatureRepository
        extends JpaRepository<EmployeeByActivityNature, Long> {

    @Query("SELECT eban FROM EmployeeByActivityNature eban " +
           "JOIN FETCH eban.activityNature " +
           "WHERE eban.employee.id = :employeeId " +
           "  AND eban.startDate <= :searchEnd " +
           "  AND (eban.endDate IS NULL OR eban.endDate >= :searchStart) " +
           "ORDER BY eban.startDate DESC")
    Optional<EmployeeByActivityNature> findActiveByEmployeeId(
            @Param("employeeId")  Long      employeeId,
            @Param("searchStart") LocalDate searchStart,
            @Param("searchEnd")   LocalDate searchEnd
    );

    // ── Product Manager dashboard aggregations ───────────────────────────────

    /**
     * Activity nature breakdown for product-related timesheets.
     * Joins employee_time → accounting_code → activity_nature.
     * Excludes HOLIDAYS and excluded OUs.
     */
    @Query(value =
        "SELECT " +
        "    UPPER(an.name)                                                AS natureName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "JOIN employee e ON e.id = et.employee_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd " +
        "  AND ac.product_id IS NOT NULL " +
        "  AND UPPER(an.name) != 'HOLIDAYS' " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= :monthEnd " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= :monthStart) " +
        "  ) " +
        "GROUP BY UPPER(an.name) " +
        "ORDER BY totalManDays DESC",
        nativeQuery = true)
    List<Object[]> sumManDaysByActivityNatureForProducts(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );
}
