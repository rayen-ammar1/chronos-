package com.chronos.repository;

import com.chronos.entity.ExcludedOrganizationalUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExcludedOrganizationalUnitRepository
        extends JpaRepository<ExcludedOrganizationalUnit, Long> {

    /**
     * Returns true if the employee's current OU is on the exclusion list.
     * Excluded OU members are exempt from timesheet obligation per spec §3.1.2.
     */
    @Query("SELECT COUNT(eou) > 0 FROM ExcludedOrganizationalUnit eou " +
           "JOIN OrganizationalUnitMember oum " +
           "  ON oum.organizationalUnit.id = eou.organizationalUnit.id " +
           "WHERE oum.employee.id = :employeeId")
    boolean isEmployeeInExcludedOu(@Param("employeeId") Long employeeId);
}
