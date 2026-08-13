package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Tracks which Product an employee is assigned to over time.
 * Used in the fallback path alongside OrganizationalUnitMember
 * to resolve the full default cost allocation context.
 *
 * Note: Product = "NA" observed in Org Assignment source data.
 * The Python importer should map "NA" to NULL (no product).
 */
@Entity
@Table(name = "employee_by_product")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EmployeeByProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}
