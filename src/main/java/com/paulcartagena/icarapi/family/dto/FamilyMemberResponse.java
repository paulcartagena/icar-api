package com.paulcartagena.icarapi.family.dto;

import com.paulcartagena.icarapi.member.enums.MemberStatus;

import java.util.UUID;

public record FamilyMemberResponse(
        UUID id,
        String firstName,
        String lastName,
        MemberStatus status
) {
}
