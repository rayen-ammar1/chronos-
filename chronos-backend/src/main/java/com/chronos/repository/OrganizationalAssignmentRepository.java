package com.chronos.repository;

import com.chronos.entity.OrganizationalAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrganizationalAssignmentRepository
        extends JpaRepository<OrganizationalAssignment, Long> {

    /**
     * Step 1 check — does this employee have a pre-configured cost allocation?
     * If list is non-empty, report uses these rows directly and skips Step 2.
     * JOIN FETCH prevents N+1 when iterating assignments to build report lines.
     */
    @Query("SELECT oa FROM OrganizationalAssignment oa " +
           "LEFT JOIN FETCH oa.organizationalUnit ou " +
           "LEFT JOIN FETCH ou.parent " +
           "LEFT JOIN FETCH oa.accountingCode ac " +
           "LEFT JOIN FETCH ac.activityNature " +
           "LEFT JOIN FETCH oa.product " +
           "WHERE oa.employee.id = :employeeId " +
           "ORDER BY oa.allocationPercentage DESC")
    List<OrganizationalAssignment> findByEmployeeId(@Param("employeeId") Long employeeId);
}
