package com.paulcartagena.icarapi.family.service;

import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.family.dto.FamilyMemberResponse;
import com.paulcartagena.icarapi.family.dto.FamilyRequest;
import com.paulcartagena.icarapi.family.dto.FamilyResponse;
import com.paulcartagena.icarapi.family.entity.Family;
import com.paulcartagena.icarapi.family.repository.FamilyRepository;
import com.paulcartagena.icarapi.member.entity.Member;
import com.paulcartagena.icarapi.member.repository.MemberRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final MemberRepository memberRepository;

    public FamilyService(FamilyRepository familyRepository, MemberRepository memberRepository) {
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<FamilyResponse> getAllFamilies() {
        return familyRepository.findAll(Sort.by("familyName")).stream()
                .map(this::buildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FamilyResponse getFamilyById(UUID id) {
        return buildResponse(findFamily(id));
    }

    // The responsible joins the new family (leaving any family they were in, where they weren't responsible).
    // Other members are added from /members by setting their familyId.
    public FamilyResponse createFamily(FamilyRequest familyRequest) {
        Member responsible = findMember(familyRequest.responsibleMemberId());

        if (familyRepository.existsByResponsibleMemberId(responsible.getId())) {
            throw ApiException.conflict("Member is already responsible for another family.");
        }

        Family family = new Family();
        family.setFamilyName(familyRequest.familyName().trim());
        family.setNotes(blankToNull(familyRequest.notes()));
        family.setResponsibleMember(responsible);
        Family savedFamily = familyRepository.save(family);

        if (responsible.getFamily() != null) {
            responsible.getFamily().getMembers().remove(responsible);
        }
        responsible.setFamily(savedFamily);
        savedFamily.getMembers().add(responsible);

        return buildResponse(savedFamily);
    }

    public FamilyResponse updateFamily(UUID id, FamilyRequest familyRequest) {
        Family family = findFamily(id);

        if (!family.getResponsibleMember().getId().equals(familyRequest.responsibleMemberId())) {
            Member responsible = findMember(familyRequest.responsibleMemberId());

            // The new responsible must already be a member of this family
            if (responsible.getFamily() == null || !responsible.getFamily().getId().equals(id)) {
                throw ApiException.conflict("The responsible member must belong to the family.");
            }
            family.setResponsibleMember(responsible);
        }

        family.setFamilyName(familyRequest.familyName().trim());
        family.setNotes(blankToNull(familyRequest.notes()));
        return buildResponse(family);
    }

    // Only a family left with just its responsible can be deleted; the responsible stays as a member without family
    public void deleteFamily(UUID id) {
        Family family = findFamily(id);
        Member responsible = family.getResponsibleMember();

        boolean hasOtherMembers = family.getMembers().stream()
                .anyMatch(member -> !member.getId().equals(responsible.getId()));
        if (hasOtherMembers) {
            throw ApiException.conflict("Family still has members; move them to another family first.");
        }

        responsible.setFamily(null);
        familyRepository.delete(family);
    }

    private Family findFamily(UUID id) {
        return familyRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Family not found: " + id));
    }

    private Member findMember(UUID id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Member not found: " + id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private FamilyResponse buildResponse(Family family) {
        Member responsible = family.getResponsibleMember();
        List<FamilyMemberResponse> members = family.getMembers().stream()
                .sorted(Comparator.comparing(Member::getLastName).thenComparing(Member::getFirstName))
                .map(member -> new FamilyMemberResponse(
                        member.getId(),
                        member.getFirstName(),
                        member.getLastName(),
                        member.getStatus()))
                .toList();

        return new FamilyResponse(
                family.getId(),
                family.getFamilyName(),
                family.getNotes(),
                responsible.getId(),
                responsible.getFirstName() + " " + responsible.getLastName(),
                members
        );
    }
}
