package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * ADDED — was missing from original class diagram.
 * Referenced in spec section 3.1.2.
 *
 * OUs in this table are exempt from the timesheet obligation.
 * During report generation, employees belonging to an excluded OU
 * are not expected to have timesheets and are handled via the
 * "no timesheets" branch with their defaults.
 *
 * One row per excluded OU — use the unique constraint to prevent duplicates.
 */
@Entity
@Table(name = "excluded_organizational_unit")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ExcludedOrganizationalUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizational_unit_id", nullable = false, unique = true)
    private OrganizationalUnit organizationalUnit;
}
