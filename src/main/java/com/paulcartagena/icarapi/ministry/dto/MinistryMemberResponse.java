package com.paulcartagena.icarapi.ministry.dto;

import com.paulcartagena.icarapi.member.enums.MemberStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MinistryMemberResponse(
        UUID memberId,
        String firstName,
        String lastName,
        MemberStatus status,
        LocalDateTime joinedAt
) {
}
