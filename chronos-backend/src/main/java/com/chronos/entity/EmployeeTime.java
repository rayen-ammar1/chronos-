package com.chronos.entity;

import com.chronos.enums.TimesheetStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Individual timesheet entry.
 * Grouped by accountingCode in the Step 2 report query.
 *
 * FIXED: status was String — now TimesheetStatus enum.
 *        Python cleaner must normalize source value "Closed" → "CLOSED".
 * FIXED: validatorId, creatorUserId, updatorUserId were BIGINT —
 *        source data contains string usernames (e.g. "rogmorgan").
 *        Renamed to validatorUsername etc. and typed as VARCHAR(50).
 *
 * CRITICAL — MAN DAY vs ELAPSED TIME:
 *   elapsedTime = hours booked (source unit, ratio is 8 hours = 1 day)
 *   manDay      = business days booked (the ONLY field used in report calculations)
 *   Never use elapsedTime for report ratios or capacity comparisons.
 */
@Entity
@Table(name = "employee_time")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EmployeeTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    /**
     * Hours booked. Source unit — 8 elapsed = 1 man-day.
     * DO NOT use in report calculations. Use manDay instead.
     */
    @Column(name = "elapsed_time", nullable = false)
    private Double elapsedTime;

    /**
     * Business days booked.
     * THE ONLY field to use for ratios, sums, and capacity comparisons.
     * All report output lines are derived from this value.
     */
    @Column(name = "man_day")
    private Double manDay;

    /**
     * FIXED: was String. "Closed" in source — Python must uppercase before insert.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private TimesheetStatus status;

    /**
     * FIXED: was BIGINT validatorId. Source contains string usernames.
     */
    @Column(name = "validator_username", length = 50)
    private String validatorUsername;

    /**
     * FIXED: was BIGINT creatorUserId. Source contains string usernames.
     */
    @Column(name = "creator_username", length = 50)
    private String creatorUsername;

    /**
     * FIXED: was BIGINT updatorUserId. Source contains string usernames.
     */
    @Column(name = "updator_username", length = 50)
    private String updatorUsername;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "price_increase_reason")
    private String priceIncreaseReason;

    @Column(name = "creation_date")
    private LocalDate creationDate;

    @Column(name = "update_date")
    private LocalDate updateDate;

    /**
     * Physical site of the work. e.g. "ATVERME G".
     * Present in every CSV row — added to schema from data analysis.
     */
    @Column(name = "site", length = 100)
    private String site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounting_code_id", nullable = false)
    private AccountingCode accountingCode;
}
