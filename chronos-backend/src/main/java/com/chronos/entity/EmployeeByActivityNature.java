package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Tracks which ActivityNature applies to an employee over time.
 * Used in the fallback path alongside OrganizationalUnitMember
 * and EmployeeByProduct to complete the default cost allocation context.
 */
@Entity
@Table(name = "employee_by_activity_nature")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EmployeeByActivityNature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_nature_id", nullable = false)
    private ActivityNature activityNature;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}
