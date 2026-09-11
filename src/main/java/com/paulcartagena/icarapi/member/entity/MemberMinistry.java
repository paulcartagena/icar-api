package com.paulcartagena.icarapi.member.entity;

import com.paulcartagena.icarapi.ministry.entity.Ministry;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "member_ministry")
public class MemberMinistry {
    @EmbeddedId
    private MemberMinistryId id = new MemberMinistryId();

    @ManyToOne
    @MapsId("memberId")
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne
    @MapsId("ministryId")
    @JoinColumn(name = "ministry_id")
    private Ministry ministry;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
}
