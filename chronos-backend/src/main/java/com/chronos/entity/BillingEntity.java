package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "billing_entity")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BillingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
}
