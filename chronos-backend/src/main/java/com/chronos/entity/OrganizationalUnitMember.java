package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Tracks which OrganizationalUnit an employee belongs to over time.
 * Used in the fallback path (no timesheets / holidays-only) to resolve
 * the employee's OU and its parent for the NO_TS_ output line prefix.
 *
 * Date overlap query pattern (same logic as CompanyMember):
 *   WHERE employee_id = :id
 *     AND start_date <= :searchEnd
 *     AND (end_date IS NULL OR end_date >= :searchStart)
 */
@Entity
@Table(name = "organizational_unit_member")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrganizationalUnitMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizational_unit_id", nullable = false)
    private OrganizationalUnit organizationalUnit;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}
