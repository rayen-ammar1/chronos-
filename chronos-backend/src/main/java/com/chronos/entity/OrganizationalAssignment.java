package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Default cost allocation for an employee (Step 1 of report generation).
 *
 * If one or more rows exist for an employee, Step 1 uses them directly
 * to produce output lines and skips Step 2 entirely.
 *
 * OPEN QUESTION — split allocations:
 *   Source data contains employees with allocationPercentage < 100%
 *   (e.g. 15%, 30%, 40%). It is unclear whether:
 *   (a) multiple rows per employee always sum to 100%, or
 *   (b) partial allocation is valid and the remainder comes from timesheets.
 *   This changes the Step 1 → Step 2 decision logic. Clarify before
 *   implementing ReportGenerationService.
 *
 * Note: Product can be NULL — "NA" in source data maps to no product.
 */
@Entity
@Table(name = "organizational_assignment")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrganizationalAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizational_unit_id", nullable = false)
    private OrganizationalUnit organizationalUnit;

    /**
     * NULL when the source data value is "NA" (no product assigned).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_code_id", nullable = false)
    private AccountingCode accountingCode;

    /**
     * The project this assignment is tied to.
     * Used by the Product Manager dashboard to aggregate man-days by project.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /**
     * Percentage of time allocated to this accounting code.
     * Constraint: 0 < allocationPercentage <= 100.
     * OPEN QUESTION: do multiple rows per employee always sum to 100%?
     */
    @Column(name = "allocation_percentage", nullable = false)
    private Double allocationPercentage;
}
