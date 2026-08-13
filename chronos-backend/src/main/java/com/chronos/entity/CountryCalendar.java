package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Working-day calendar per country.
 * EmployeeCapacityService counts rows WHERE is_working_day = true
 * between searchStart and searchEnd to compute employee capacity.
 * This is then compared against manDay totals to detect incomplete bookings.
 */
@Entity
@Table(
    name = "country_calendar",
    uniqueConstraints = @UniqueConstraint(columnNames = {"country", "date"})
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CountryCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "is_working_day", nullable = false)
    private Boolean isWorkingDay;
}
