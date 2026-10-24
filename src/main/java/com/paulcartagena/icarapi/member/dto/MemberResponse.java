package com.paulcartagena.icarapi.member.dto;

import com.paulcartagena.icarapi.member.enums.MemberStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// familyResponsible: true when this member is the responsible of their family
public record MemberResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate joinDate,
        MemberStatus status,
        UUID familyId,
        String familyName,
        boolean familyResponsible,
        List<MemberMinistryResponse> ministries
) {
}
