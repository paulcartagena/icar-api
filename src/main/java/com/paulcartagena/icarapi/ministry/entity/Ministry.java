package com.paulcartagena.icarapi.ministry.entity;

import com.paulcartagena.icarapi.member.entity.MemberMinistry;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ministry")
public class Ministry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 100, nullable = false)
    private String name;

    private String description;

    @OneToMany(mappedBy = "ministry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceSchedule> services = new ArrayList<>();

    @OneToMany(mappedBy = "ministry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberMinistry> members = new ArrayList<>();
}
