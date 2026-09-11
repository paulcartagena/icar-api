package com.paulcartagena.icarapi.family.entity;

import com.paulcartagena.icarapi.member.entity.Member;
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
@Table(name = "family")
public class Family {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "responsible_member_id", nullable = false, unique = true)
    private Member responsibleMember;

    @Column(name = "family_name", length = 100, nullable = false)
    private String familyName;

    @Column(length = 150)
    private String notes;

    @OneToMany(mappedBy = "family")
    private List<Member> members = new ArrayList<>();
}
