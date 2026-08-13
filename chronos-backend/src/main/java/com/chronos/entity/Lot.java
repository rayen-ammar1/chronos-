package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lot")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
}
