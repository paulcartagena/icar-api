package com.paulcartagena.icarapi.member.entity;

import com.paulcartagena.icarapi.family.entity.Family;
import com.paulcartagena.icarapi.member.enums.MemberStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "family_id")
    private Family family;

    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    @Column(length = 150)
    private String email;

    @Column(length = 25)
    private String phone;

    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 25, nullable = false)
    private MemberStatus status;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberMinistry> ministries = new ArrayList<>();
}
