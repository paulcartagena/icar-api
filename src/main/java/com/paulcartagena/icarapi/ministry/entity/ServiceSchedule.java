package com.paulcartagena.icarapi.ministry.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "service_schedule")
public class ServiceSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "ministry_id")
    private Ministry ministry;

    @Column(name = "day_of_week", length = 25, nullable = false)
    private String dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;
}
