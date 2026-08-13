package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "client")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
}
