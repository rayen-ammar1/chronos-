package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/**
 * PENDING CLARIFICATION: isCapitalizableBy in source data contains
 * string usernames (e.g. "wenmorris"), not enum values.
 * Kept as VARCHAR(100) until the business confirms the intended values.
 * Once clarified, replace with an appropriate enum or FK to a user table.
 */
@Entity
@Table(name = "phase")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Phase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "deliverable_name")
    private String deliverableName;

    @Column(name = "is_capitalizable", nullable = false)
    private Boolean isCapitalizable = false;

    @Column(name = "capitalizable_date")
    private LocalDate capitalizableDate;

    /**
     * PENDING — source CSV contained usernames here, not enum values.
     * Do NOT add an enum until business clarification is received.
     */
    @Column(name = "is_capitalizable_by", length = 100)
    private String isCapitalizableBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iteration_id", nullable = false)
    private Iteration iteration;
}
