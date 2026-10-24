package com.paulcartagena.icarapi.member.service;

import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.family.entity.Family;
import com.paulcartagena.icarapi.family.repository.FamilyRepository;
import com.paulcartagena.icarapi.member.dto.MemberMinistryResponse;
import com.paulcartagena.icarapi.member.dto.MemberRequest;
import com.paulcartagena.icarapi.member.dto.MemberResponse;
import com.paulcartagena.icarapi.member.entity.Member;
import com.paulcartagena.icarapi.member.entity.MemberMinistry;
import com.paulcartagena.icarapi.member.enums.MemberStatus;
import com.paulcartagena.icarapi.member.repository.MemberRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.paulcartagena.icarapi.member.repository.MemberSpecifications.*;

@Service
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final FamilyRepository familyRepository;

    public MemberService(MemberRepository memberRepository, FamilyRepository familyRepository) {
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getAllMembers(MemberStatus status, UUID familyId, UUID ministryId, String search) {
        Sort sort = Sort.by("lastName", "firstName");

        Specification<Member> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and(hasStatus(status));
        }
        if (familyId != null) {
            spec = spec.and(inFamily(familyId));
        }
        if (ministryId != null) {
            spec = spec.and(inMinistry(ministryId));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(nameContains(search.trim()));
        }

        return memberRepository.findAll(spec, sort).stream()
                .map(this::buildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberResponse getMemberById(UUID id) {
        return buildResponse(findMember(id));
    }

    public MemberResponse createMember(MemberRequest memberRequest) {
        Member member = new Member();
        applyFields(member, memberRequest);
        member.setFamily(memberRequest.familyId() != null ? findFamily(memberRequest.familyId()) : null);

        Member savedMember = memberRepository.save(member);
        return buildResponse(savedMember);
    }

    // Members are never deleted: a member who leaves is marked INACTIVE (or DECEASED)
    public MemberResponse updateMember(UUID id, MemberRequest memberRequest) {
        Member member = findMember(id);

        UUID currentFamilyId = member.getFamily() != null ? member.getFamily().getId() : null;
        if (!Objects.equals(currentFamilyId, memberRequest.familyId())) {
            // The responsible always belongs to their family, so they can't leave it
            if (isFamilyResponsible(member)) {
                throw ApiException.conflict(
                        "Member is responsible for their family; assign another responsible first.");
            }
            member.setFamily(memberRequest.familyId() != null ? findFamily(memberRequest.familyId()) : null);
        }

        applyFields(member, memberRequest);
        return buildResponse(member);
    }

    private Member findMember(UUID id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Member not found: " + id));
    }

    private Family findFamily(UUID id) {
        return familyRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Family not found: " + id));
    }

    private void applyFields(Member member, MemberRequest memberRequest) {
        member.setFirstName(memberRequest.firstName().trim());
        member.setLastName(memberRequest.lastName().trim());
        member.setEmail(blankToNull(memberRequest.email()));
        member.setPhone(blankToNull(memberRequest.phone()));
        member.setJoinDate(memberRequest.joinDate());
        member.setStatus(memberRequest.status());
    }

    private static boolean isFamilyResponsible(Member member) {
        return member.getFamily() != null
                && member.getFamily().getResponsibleMember().getId().equals(member.getId());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private MemberResponse buildResponse(Member member) {
        Family family = member.getFamily();
        List<MemberMinistryResponse> ministries = member.getMinistries().stream()
                .sorted(Comparator.comparing((MemberMinistry mm) -> mm.getMinistry().getName()))
                .map(mm -> new MemberMinistryResponse(
                        mm.getMinistry().getId(),
                        mm.getMinistry().getName(),
                        mm.getJoinedAt()))
                .toList();

        return new MemberResponse(
                member.getId(),
                member.getFirstName(),
                member.getLastName(),
                member.getEmail(),
                member.getPhone(),
                member.getJoinDate(),
                member.getStatus(),
                family != null ? family.getId() : null,
                family != null ? family.getFamilyName() : null,
                isFamilyResponsible(member),
                ministries
        );
    }
}
