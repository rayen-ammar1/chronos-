package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Stores activity nature values: SUPPORT, REGIE, FORFAIT, RPS, MAINTENANCE, HOLIDAYS.
 * The value "HOLIDAYS" is specifically referenced in the report logic
 * to detect full-holiday months (Step 2 branch detection).
 */
@Entity
@Table(name = "activity_nature")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ActivityNature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;
}
