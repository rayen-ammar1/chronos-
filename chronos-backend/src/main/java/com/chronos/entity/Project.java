package com.chronos.entity;

import com.chronos.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;

/**
 * Top of the project hierarchy: Project → Lot → Iteration → Phase → Activity.
 *
 * FIXED: projectManager was String — now a proper FK to Employee.
 * FIXED: status was String — now a typed enum.
 * NOTE:  status is nullable — source CSV has NULL values for this field.
 */
@Entity
@Table(name = "project")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * Resolved FK to Employee after import.
     * FIXED: was String in original class diagram.
     * Set by the Python importer after resolving projectManagerUsername.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_manager_id")
    private Employee projectManager;

    /**
     * Raw username string from source CSV (e.g. "steanders").
     * Kept for import traceability. Use projectManager FK for queries.
     */
    @Column(name = "project_manager_username", length = 50)
    private String projectManagerUsername;

    /**
     * FIXED: was String. Now enum. Nullable — NULL in source data.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ProjectStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_entity_id")
    private BillingEntity billingEntity;
}
