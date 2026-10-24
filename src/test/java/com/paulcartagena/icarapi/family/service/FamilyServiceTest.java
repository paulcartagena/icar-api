package com.paulcartagena.icarapi.family.service;

import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.family.dto.FamilyRequest;
import com.paulcartagena.icarapi.family.dto.FamilyResponse;
import com.paulcartagena.icarapi.family.entity.Family;
import com.paulcartagena.icarapi.family.repository.FamilyRepository;
import com.paulcartagena.icarapi.member.entity.Member;
import com.paulcartagena.icarapi.member.enums.MemberStatus;
import com.paulcartagena.icarapi.member.repository.MemberRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private FamilyService familyService;

    @Nested
    class CreateFamily {

        @Test
        void responsibleJoinsTheNewFamily() {
            Member responsible = member("Ana");
            Family previous = family(member("Luis"));
            previous.getMembers().add(responsible);
            responsible.setFamily(previous);
            when(memberRepository.findById(responsible.getId())).thenReturn(Optional.of(responsible));
            when(familyRepository.existsByResponsibleMemberId(responsible.getId())).thenReturn(false);
            when(familyRepository.save(any(Family.class))).thenAnswer(invocation -> invocation.getArgument(0));

            FamilyResponse response = familyService.createFamily(request(responsible.getId()));

            assertThat(response.familyName()).isEqualTo("Pérez");
            assertThat(response.responsibleMemberId()).isEqualTo(responsible.getId());
            assertThat(response.members()).extracting("id").containsExactly(responsible.getId());
            assertThat(responsible.getFamily()).isNotSameAs(previous);
            assertThat(previous.getMembers()).doesNotContain(responsible);
        }

        @Test
        void rejectsMemberAlreadyResponsibleForAnotherFamily() {
            Member responsible = member("Ana");
            when(memberRepository.findById(responsible.getId())).thenReturn(Optional.of(responsible));
            when(familyRepository.existsByResponsibleMemberId(responsible.getId())).thenReturn(true);

            assertApiException(() -> familyService.createFamily(request(responsible.getId())), HttpStatus.CONFLICT);
            verify(familyRepository, never()).save(any());
        }

        @Test
        void returnsNotFoundForUnknownMember() {
            UUID memberId = UUID.randomUUID();
            when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

            assertApiException(() -> familyService.createFamily(request(memberId)), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class UpdateFamily {

        @Test
        void changesResponsibleToAMemberOfTheFamily() {
            Family family = family(member("Ana"));
            Member luis = member("Luis");
            luis.setFamily(family);
            when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));
            when(memberRepository.findById(luis.getId())).thenReturn(Optional.of(luis));

            FamilyResponse response = familyService.updateFamily(family.getId(), request(luis.getId()));

            assertThat(response.responsibleMemberId()).isEqualTo(luis.getId());
        }

        @Test
        void rejectsResponsibleFromOutsideTheFamily() {
            Family family = family(member("Ana"));
            Member outsider = member("Luis");
            when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));
            when(memberRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

            assertApiException(() -> familyService.updateFamily(family.getId(), request(outsider.getId())),
                    HttpStatus.CONFLICT);
        }
    }

    @Nested
    class DeleteFamily {

        @Test
        void deletesFamilyWithOnlyItsResponsible() {
            Member responsible = member("Ana");
            Family family = family(responsible);
            responsible.setFamily(family);
            family.getMembers().add(responsible);
            when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));

            familyService.deleteFamily(family.getId());

            assertThat(responsible.getFamily()).isNull();
            verify(familyRepository).delete(family);
        }

        @Test
        void rejectsFamilyWithOtherMembers() {
            Member responsible = member("Ana");
            Family family = family(responsible);
            family.getMembers().add(responsible);
            family.getMembers().add(member("Luis"));
            when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));

            assertApiException(() -> familyService.deleteFamily(family.getId()), HttpStatus.CONFLICT);
            verify(familyRepository, never()).delete(any());
        }
    }

    private static void assertApiException(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(status);
    }

    private static Member member(String firstName) {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        member.setFirstName(firstName);
        member.setLastName("Pérez");
        member.setJoinDate(LocalDate.of(2024, 3, 10));
        member.setStatus(MemberStatus.ACTIVE);
        return member;
    }

    private static Family family(Member responsible) {
        Family family = new Family();
        family.setId(UUID.randomUUID());
        family.setFamilyName("Pérez");
        family.setResponsibleMember(responsible);
        return family;
    }

    private static FamilyRequest request(UUID responsibleMemberId) {
        return new FamilyRequest(" Pérez ", null, responsibleMemberId);
    }
}
