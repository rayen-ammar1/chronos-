package com.chronos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * ISO country code or country name.
     * Used to join against CountryCalendar for capacity calculation.
     */
    @Column(nullable = false, length = 100)
    private String country;
}
