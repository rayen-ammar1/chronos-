package com.chronos.repository;

import com.chronos.entity.MonthPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MonthPeriodRepository extends JpaRepository<MonthPeriod, Long> {
    Optional<MonthPeriod> findByYearAndMonth(Integer year, Integer month);
}
