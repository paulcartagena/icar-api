package com.paulcartagena.icarapi.family.dto;

import java.util.List;
import java.util.UUID;

public record FamilyResponse(
        UUID id,
        String familyName,
        String notes,
        UUID responsibleMemberId,
        String responsibleMemberName,
        List<FamilyMemberResponse> members
) {
}
