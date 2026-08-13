package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * ADDED — was missing from original class diagram.
 *
 * The single mandatory input to the report generation flow.
 * Persisting it enables audit trails: which periods were generated,
 * when, and by whom.
 *
 * Source CSV format: "1|2024" (month|year).
 * The Python cleaning service parses this into year + month fields.
 * startDate and endDate are derived: startDate = first day of month,
 * endDate = last day of month.
 */
@Entity
@Table(
    name = "month_period",
    uniqueConstraints = @UniqueConstraint(columnNames = {"year", "month"})
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MonthPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer month;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Original label from the source CSV (e.g. "1|2024").
     * Kept for traceability and import debugging.
     */
    @Column(name = "source_label", length = 20)
    private String sourceLabel;
}
