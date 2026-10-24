package com.paulcartagena.icarapi.member.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MemberMinistryResponse(
        UUID ministryId,
        String ministryName,
        LocalDateTime joinedAt
) {
}
