package com.paulcartagena.icarapi.member.service;

import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.family.entity.Family;
import com.paulcartagena.icarapi.family.repository.FamilyRepository;
import com.paulcartagena.icarapi.member.dto.MemberRequest;
import com.paulcartagena.icarapi.member.dto.MemberResponse;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final LocalDate JOIN_DATE = LocalDate.of(2024, 3, 10);

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FamilyRepository familyRepository;

    @InjectMocks
    private MemberService memberService;

    @Nested
    class CreateMember {

        @Test
        void trimsNamesAndStoresBlankContactAsNull() {
            when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

            MemberResponse response = memberService.createMember(
                    new MemberRequest("  Ana ", " Pérez ", " ", "", JOIN_DATE, MemberStatus.NEW, null));

            assertThat(response.firstName()).isEqualTo("Ana");
            assertThat(response.lastName()).isEqualTo("Pérez");
            assertThat(response.email()).isNull();
            assertThat(response.phone()).isNull();
            assertThat(response.familyId()).isNull();
            assertThat(response.familyResponsible()).isFalse();
            verifyNoInteractions(familyRepository);
        }

        @Test
        void assignsFamily() {
            Family family = family(member());
            when(familyRepository.findById(family.getId())).thenReturn(Optional.of(family));
            when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

            MemberResponse response = memberService.createMember(request(family.getId()));

            assertThat(response.familyId()).isEqualTo(family.getId());
            assertThat(response.familyName()).isEqualTo("Pérez");
        }

        @Test
        void returnsNotFoundForUnknownFamily() {
            UUID familyId = UUID.randomUUID();
            when(familyRepository.findById(familyId)).thenReturn(Optional.empty());

            assertApiException(() -> memberService.createMember(request(familyId)), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class UpdateMember {

        @Test
        void movesRegularMemberToAnotherFamily() {
            Member member = member();
            Family current = family(member());
            member.setFamily(current);
            Family other = family(member());
            when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
            when(familyRepository.findById(other.getId())).thenReturn(Optional.of(other));

            MemberResponse response = memberService.updateMember(member.getId(), request(other.getId()));

            assertThat(response.familyId()).isEqualTo(other.getId());
        }

        @Test
        void rejectsResponsibleLeavingTheirFamily() {
            Member responsible = member();
            Family family = family(responsible);
            responsible.setFamily(family);
            when(memberRepository.findById(responsible.getId())).thenReturn(Optional.of(responsible));

            assertApiException(() -> memberService.updateMember(responsible.getId(), request(null)),
                    HttpStatus.CONFLICT);
            assertThat(responsible.getFamily()).isSameAs(family);
        }

        @Test
        void responsibleCanUpdateOtherFieldsKeepingTheFamily() {
            Member responsible = member();
            Family family = family(responsible);
            responsible.setFamily(family);
            when(memberRepository.findById(responsible.getId())).thenReturn(Optional.of(responsible));

            MemberResponse response = memberService.updateMember(responsible.getId(), new MemberRequest(
                    "Ana", "Pérez", "ana@mail.com", null, JOIN_DATE, MemberStatus.ACTIVE, family.getId()));

            assertThat(response.status()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(response.email()).isEqualTo("ana@mail.com");
            assertThat(response.familyResponsible()).isTrue();
            verifyNoInteractions(familyRepository);
        }

        @Test
        void returnsNotFoundWhenMissing() {
            UUID id = UUID.randomUUID();
            when(memberRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> memberService.updateMember(id, request(null)), HttpStatus.NOT_FOUND);
        }
    }

    private static void assertApiException(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(status);
    }

    private static Member member() {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        member.setFirstName("Ana");
        member.setLastName("Pérez");
        member.setJoinDate(JOIN_DATE);
        member.setStatus(MemberStatus.NEW);
        return member;
    }

    private static Family family(Member responsible) {
        Family family = new Family();
        family.setId(UUID.randomUUID());
        family.setFamilyName("Pérez");
        family.setResponsibleMember(responsible);
        return family;
    }

    private static MemberRequest request(UUID familyId) {
        return new MemberRequest("Ana", "Pérez", null, null, JOIN_DATE, MemberStatus.NEW, familyId);
    }
}
