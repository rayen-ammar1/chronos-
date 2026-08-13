package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Core employee identity.
 * The 'identifier' field (e.g. "RR28587") is the business key used
 * in CSV imports, org assignment sheets, and all cross-references.
 * Never use the auto-generated 'id' as a join key in business logic.
 */
@Entity
@Table(name = "employee")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /**
     * Business identifier from source system (e.g. "RR28587", "TG43634").
     * Unique and used as the import join key across all CSV sources.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String identifier;
}
