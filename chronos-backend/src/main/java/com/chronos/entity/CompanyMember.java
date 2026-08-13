package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Links an employee to a company for a specific date range.
 * This is the MAIN DRIVER of the report loop.
 *
 * One employee can have multiple CompanyMember rows if reassigned.
 * endDate = NULL means the employee is still active at this company.
 *
 * CRITICAL: endDate in source CSV can arrive as a string-encoded
 * Excel serial (e.g. "44568.0") for some rows. The Python cleaning
 * service must normalize all date columns before insertion.
 *
 * The report query pattern (Table 1 — all 6 cases):
 *   searchStart = GREATEST(startDate, monthPeriod.startDate)
 *   searchEnd   = LEAST(COALESCE(endDate, monthPeriod.endDate), monthPeriod.endDate)
 */
@Entity
@Table(name = "company_member")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CompanyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * NULL means the employee is still active at this company.
     * Never assume a default — always check for NULL explicitly in queries.
     */
    @Column(name = "end_date")
    private LocalDate endDate;
}
