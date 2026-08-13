package com.chronos.entity;

import com.chronos.enums.BillingMode;
import jakarta.persistence.*;
import lombok.*;

/**
 * The pivot entity for all cost allocation.
 * Every report output line is tied to an AccountingCode.
 *
 * Two identifier formats exist in source data:
 *   - Long hashed codes from Employee Time CSV: e.g. "8UJUW0VWZSERUE8"
 *   - Readable short codes from Org Assignment sheet: e.g. "DAF_FIN"
 * Both formats live in operationalIdentifier.
 * OPEN QUESTION: are these from separate source systems with a mapping key?
 *
 * FIXED: billingMode was String — now a typed enum.
 */
@Entity
@Table(name = "accounting_code")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AccountingCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Business identifier used in all report output rows.
     * Unique — two formats observed: hashed (15 chars) and readable short codes.
     */
    @Column(name = "operational_identifier", nullable = false, unique = true, length = 100)
    private String operationalIdentifier;

    /**
     * FIXED: was String. "NOTAPPLICABLE" observed in source CSV data.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 20)
    private BillingMode billingMode;

    @Column(nullable = false)
    private Boolean billable = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_nature_id", nullable = false)
    private ActivityNature activityNature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizational_unit_id")
    private OrganizationalUnit organizationalUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}
