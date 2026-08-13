package com.chronos.repository;

import com.chronos.entity.AccountingCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AccountingCodeRepository extends JpaRepository<AccountingCode, Long> {

    Optional<AccountingCode> findByOperationalIdentifier(String operationalIdentifier);

    @Query("SELECT ac FROM AccountingCode ac " +
           "JOIN FETCH ac.activityNature " +
           "LEFT JOIN FETCH ac.organizationalUnit ou " +
           "LEFT JOIN FETCH ou.parent " +
           "LEFT JOIN FETCH ac.product " +
           "WHERE ac.id = :id")
    Optional<AccountingCode> findByIdWithDetails(@Param("id") Long id);
}
