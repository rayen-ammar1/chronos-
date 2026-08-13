package com.chronos.repository;

import com.chronos.entity.EmployeeByProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeByProductRepository extends JpaRepository<EmployeeByProduct, Long> {

    @Query("SELECT ebp FROM EmployeeByProduct ebp " +
           "JOIN FETCH ebp.product " +
           "WHERE ebp.employee.id = :employeeId " +
           "  AND ebp.startDate <= :searchEnd " +
           "  AND (ebp.endDate IS NULL OR ebp.endDate >= :searchStart) " +
           "ORDER BY ebp.startDate DESC")
    Optional<EmployeeByProduct> findActiveByEmployeeId(
            @Param("employeeId")  Long      employeeId,
            @Param("searchStart") LocalDate searchStart,
            @Param("searchEnd")   LocalDate searchEnd
    );

    @Query(value =
        "SELECT COUNT(DISTINCT ebp.employee_id) " +
        "FROM employee_by_product ebp " +
        "JOIN employee e ON e.id = ebp.employee_id " +
        "WHERE ebp.start_date <= :monthEnd " +
        "  AND (ebp.end_date IS NULL OR ebp.end_date >= :monthStart) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= :monthEnd " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= :monthStart) " +
        "  )",
        nativeQuery = true)
    Integer countEmployeesWithProduct(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT COALESCE(SUM(et.man_day), 0) " +
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
        "  )",
        nativeQuery = true)
    Double sumManDaysOnProducts(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT COALESCE(SUM(CAST(cc.is_working_day AS integer)), 0) " +
        "FROM employee_by_product ebp " +
        "JOIN employee e ON e.id = ebp.employee_id " +
        "JOIN company_member cm ON cm.employee_id = e.id " +
        "JOIN company co ON co.id = cm.company_id " +
        "JOIN country_calendar cc ON cc.country = co.country " +
        "WHERE ebp.start_date <= :monthEnd " +
        "  AND (ebp.end_date IS NULL OR ebp.end_date >= :monthStart) " +
        "  AND cc.date BETWEEN :monthStart AND :monthEnd " +
        "  AND cc.is_working_day = TRUE " +
        "  AND cm.start_date <= :monthEnd " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= :monthStart) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= :monthEnd " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= :monthStart) " +
        "  )",
        nativeQuery = true)
    Integer sumCapacityForProductEmployees(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT " +
        "    p.name                                                       AS productName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "JOIN employee e ON e.id = et.employee_id " +
        "JOIN product p ON p.id = ac.product_id " +
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
        "GROUP BY p.name " +
        "ORDER BY totalManDays DESC",
        nativeQuery = true)
    List<Object[]> sumManDaysByProduct(
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

    // 🚀 FIX: Corrected join chain for Top Projects
    @Query(value =
        "SELECT " +
        "    pr.name                                                       AS projectName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac  ON ac.id = et.accounting_code_id " +
        "JOIN activity        act ON act.id = ac.activity_id " +
        "JOIN phase           ph  ON ph.id  = act.phase_id " +
        "JOIN iteration       it  ON it.id  = ph.iteration_id " +
        "JOIN lot             l   ON l.id   = it.lot_id " +
        "JOIN project         pr  ON pr.id  = l.project_id " +
        "JOIN activity_nature an  ON an.id  = ac.activity_nature_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd " +
        "  AND UPPER(an.name) != 'HOLIDAYS' " +
        "GROUP BY pr.name " +
        "ORDER BY totalManDays DESC " +
        "LIMIT 10",
        nativeQuery = true)
    List<Object[]> sumManDaysByProject(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT " +
        "    co.name                                                       AS companyName, " +
        "    COUNT(DISTINCT ebp.employee_id)                               AS headcount " +
        "FROM employee_by_product ebp " +
        "JOIN employee e ON e.id = ebp.employee_id " +
        "JOIN company_member cm ON cm.employee_id = e.id " +
        "JOIN company co ON co.id = cm.company_id " +
        "WHERE ebp.start_date <= :monthEnd " +
        "  AND (ebp.end_date IS NULL OR ebp.end_date >= :monthStart) " +
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
        "ORDER BY headcount DESC",
        nativeQuery = true)
    List<Object[]> countHeadcountByCompany(
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd")   LocalDate monthEnd
    );

    @Query(value =
        "SELECT " +
        "    p.name                                                       AS productName, " +
        "    SUM(et.man_day)                                               AS totalManDays " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "JOIN employee e ON e.id = et.employee_id " +
        "JOIN product p ON p.id = ac.product_id " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE et.date BETWEEN mp.start_date AND mp.end_date " +
        "  AND ac.product_id IS NOT NULL " +
        "  AND UPPER(an.name) != 'HOLIDAYS' " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= mp.end_date " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= mp.start_date) " +
        "  ) " +
        "GROUP BY p.name " +
        "ORDER BY totalManDays DESC",
        nativeQuery = true)
    List<Object[]> sumManDaysByProductByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    @Query(value =
        "SELECT COALESCE(SUM(et.man_day), 0) " +
        "FROM employee_time et " +
        "JOIN accounting_code ac ON ac.id = et.accounting_code_id " +
        "JOIN activity_nature an ON an.id = ac.activity_nature_id " +
        "JOIN employee e ON e.id = et.employee_id " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE et.date BETWEEN mp.start_date AND mp.end_date " +
        "  AND ac.product_id IS NOT NULL " +
        "  AND UPPER(an.name) != 'HOLIDAYS' " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= mp.end_date " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= mp.start_date) " +
        "  )",
        nativeQuery = true)
    Double sumManDaysOnProductsByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    @Query(value =
        "SELECT COALESCE(SUM(CAST(cc.is_working_day AS integer)), 0) " +
        "FROM employee_by_product ebp " +
        "JOIN employee e ON e.id = ebp.employee_id " +
        "JOIN company_member cm ON cm.employee_id = e.id " +
        "JOIN company co ON co.id = cm.company_id " +
        "JOIN country_calendar cc ON cc.country = co.country " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE ebp.start_date <= mp.end_date " +
        "  AND (ebp.end_date IS NULL OR ebp.end_date >= mp.start_date) " +
        "  AND cc.date BETWEEN mp.start_date AND mp.end_date " +
        "  AND cc.is_working_day = TRUE " +
        "  AND cm.start_date <= mp.end_date " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= mp.start_date) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM excluded_organizational_unit eou " +
        "    JOIN organizational_unit_member oum ON oum.organizational_unit_id = eou.organizational_unit_id " +
        "    WHERE oum.employee_id = e.id " +
        "      AND oum.start_date <= mp.end_date " +
        "      AND (oum.end_date IS NULL OR oum.end_date >= mp.start_date) " +
        "  )",
        nativeQuery = true)
    Integer sumCapacityForProductEmployeesByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );

    @Query(value =
        "SELECT " +
        "    co.name                                                       AS companyName, " +
        "    COUNT(DISTINCT ebp.employee_id)                               AS headcount " +
        "FROM employee_by_product ebp " +
        "JOIN employee e ON e.id = ebp.employee_id " +
        "JOIN company_member cm ON cm.employee_id = e.id " +
        "JOIN company co ON co.id = cm.company_id " +
        "JOIN month_period mp ON mp.year = :year AND mp.month = :month " +
        "WHERE ebp.start_date <= mp.end_date " +
        "  AND (ebp.end_date IS NULL OR ebp.end_date >= mp.start_date) " +
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
        "ORDER BY headcount DESC",
        nativeQuery = true)
    List<Object[]> countHeadcountByCompanyByPeriod(
            @Param("year")  Integer year,
            @Param("month") Integer month
    );
}