package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "activity")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private Phase phase;
}
