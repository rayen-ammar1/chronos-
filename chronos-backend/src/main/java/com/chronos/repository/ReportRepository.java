package com.chronos.repository;

import com.chronos.entity.EmployeeTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<EmployeeTime, Long> {

    @Query(value =
        "SELECT DISTINCT e.identifier, e.first_name, e.last_name, co.name " +
        "FROM employee e " +
        "JOIN company_member cm ON cm.employee_id = e.id " +
        "JOIN company co ON co.id = cm.company_id " +
        "WHERE cm.start_date <= :monthEnd " +
        "  AND (cm.end_date IS NULL OR cm.end_date >= :monthStart) " +
        "  AND NOT EXISTS ( " +
        "    SELECT 1 FROM employee_time et " +
        "    WHERE et.employee_id = e.id AND et.date BETWEEN :monthStart AND :monthEnd) " +
        "ORDER BY co.name, e.identifier LIMIT 500",
        nativeQuery = true)
    List<Object[]> missingTimesheets(@Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);

    @Query(value =
        "SELECT e.identifier, e.first_name, e.last_name, CAST(et.date AS text), et.man_day, CAST(et.status AS text) " +
        "FROM employee_time et " +
        "JOIN employee e ON e.id = et.employee_id " +
        "JOIN company_member cm ON cm.employee_id = e.id " +
        "JOIN company co ON co.id = cm.company_id " +
        "JOIN country_calendar cc ON cc.country = co.country AND cc.date = et.date " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd AND cc.is_working_day = FALSE " +
        "LIMIT 500",
        nativeQuery = true)
    List<Object[]> weekendWork(@Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);

    @Query(value =
        "SELECT e.identifier, e.first_name, e.last_name, CAST(et.date AS text), et.man_day, CAST(et.status AS text) " +
        "FROM employee_time et JOIN employee e ON e.id = et.employee_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd AND et.man_day > 1 " +
        "LIMIT 500",
        nativeQuery = true)
    List<Object[]> overbooked(@Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);

    @Query(value =
        "SELECT e.identifier, e.first_name, e.last_name, CAST(et.date AS text), et.man_day, CAST(et.status AS text) " +
        "FROM employee_time et JOIN employee e ON e.id = et.employee_id " +
        "WHERE et.date BETWEEN :monthStart AND :monthEnd AND CAST(et.status AS text) <> 'VALIDATED' " +
        "LIMIT 500",
        nativeQuery = true)
    List<Object[]> notValidated(@Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);
}