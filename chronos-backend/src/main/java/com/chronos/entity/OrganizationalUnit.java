package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Self-referencing hierarchy. The parent OU is used by the report
 * to build the NO_TS_ prefix for output lines.
 * Example: OU "FINANCE" has parent "SUPPORT FUNCTIONS".
 */
@Entity
@Table(name = "organizational_unit")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrganizationalUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrganizationalUnit parent;
}
