package com.chronos.repository;

import com.chronos.entity.OrganizationalUnitMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface OrganizationalUnitMemberRepository
        extends JpaRepository<OrganizationalUnitMember, Long> {

    /**
     * Fallback path — find the OU an employee belongs to during the search range.
     * Used when no timesheets exist or when the month is holidays-only.
     * Overlap condition mirrors CompanyMember:
     *   startDate <= searchEnd AND (endDate IS NULL OR endDate >= searchStart)
     * Also fetches parent OU for the NO_TS_ prefix construction.
     */
    @Query("SELECT oum FROM OrganizationalUnitMember oum " +
           "JOIN FETCH oum.organizationalUnit ou " +
           "LEFT JOIN FETCH ou.parent " +
           "WHERE oum.employee.id = :employeeId " +
           "  AND oum.startDate <= :searchEnd " +
           "  AND (oum.endDate IS NULL OR oum.endDate >= :searchStart) " +
           "ORDER BY oum.startDate DESC")
    Optional<OrganizationalUnitMember> findActiveByEmployeeId(
            @Param("employeeId")  Long      employeeId,
            @Param("searchStart") LocalDate searchStart,
            @Param("searchEnd")   LocalDate searchEnd
    );
}
