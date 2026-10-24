package com.paulcartagena.icarapi.ministry.service;

import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.member.entity.Member;
import com.paulcartagena.icarapi.member.entity.MemberMinistry;
import com.paulcartagena.icarapi.member.entity.MemberMinistryId;
import com.paulcartagena.icarapi.member.enums.MemberStatus;
import com.paulcartagena.icarapi.member.repository.MemberMinistryRepository;
import com.paulcartagena.icarapi.member.repository.MemberRepository;
import com.paulcartagena.icarapi.ministry.dto.*;
import com.paulcartagena.icarapi.ministry.entity.Ministry;
import com.paulcartagena.icarapi.ministry.entity.ServiceSchedule;
import com.paulcartagena.icarapi.ministry.repository.MinistryRepository;
import com.paulcartagena.icarapi.ministry.repository.ServiceScheduleRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinistryServiceTest {

    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Mock
    private MinistryRepository ministryRepository;

    @Mock
    private ServiceScheduleRepository serviceScheduleRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberMinistryRepository memberMinistryRepository;

    @InjectMocks
    private MinistryService ministryService;

    @Nested
    class CreateMinistry {

        @Test
        void trimsNameAndStoresBlankDescriptionAsNull() {
            when(ministryRepository.existsByNameIgnoreCase("Alabanza")).thenReturn(false);
            when(ministryRepository.save(any(Ministry.class))).thenAnswer(invocation -> invocation.getArgument(0));

            MinistryResponse response = ministryService.createMinistry(new MinistryRequest(" Alabanza ", "  "));

            assertThat(response.name()).isEqualTo("Alabanza");
            assertThat(response.description()).isNull();
            assertThat(response.memberCount()).isZero();
        }

        @Test
        void rejectsDuplicatedName() {
            when(ministryRepository.existsByNameIgnoreCase("Alabanza")).thenReturn(true);

            assertApiException(() -> ministryService.createMinistry(new MinistryRequest("Alabanza", null)),
                    HttpStatus.CONFLICT);
            verify(ministryRepository, never()).save(any());
        }
    }

    @Test
    void updateRejectsNameOfAnotherMinistry() {
        Ministry ministry = ministry();
        when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
        when(ministryRepository.existsByNameIgnoreCaseAndIdNot("Jóvenes", ministry.getId())).thenReturn(true);

        assertApiException(() -> ministryService.updateMinistry(ministry.getId(), new MinistryRequest("Jóvenes", null)),
                HttpStatus.CONFLICT);
    }

    @Nested
    class AddMember {

        @Test
        void defaultsJoinedAtToNow() {
            Ministry ministry = ministry();
            Member member = member(MemberStatus.ACTIVE);
            when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
            when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
            when(memberMinistryRepository.existsById(new MemberMinistryId(member.getId(), ministry.getId())))
                    .thenReturn(false);
            when(memberMinistryRepository.save(any(MemberMinistry.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            LocalDateTime before = LocalDateTime.now();
            MinistryMemberResponse response = ministryService.addMember(ministry.getId(),
                    new MinistryMemberRequest(member.getId(), null));

            assertThat(response.memberId()).isEqualTo(member.getId());
            assertThat(response.joinedAt()).isAfterOrEqualTo(before);
        }

        @Test
        void keepsGivenJoinedAt() {
            Ministry ministry = ministry();
            Member member = member(MemberStatus.ACTIVE);
            LocalDateTime joinedAt = LocalDateTime.of(2020, 5, 1, 0, 0);
            when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
            when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
            when(memberMinistryRepository.save(any(MemberMinistry.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            MinistryMemberResponse response = ministryService.addMember(ministry.getId(),
                    new MinistryMemberRequest(member.getId(), joinedAt));

            assertThat(response.joinedAt()).isEqualTo(joinedAt);
        }

        @Test
        void rejectsMemberAlreadyInTheMinistry() {
            Ministry ministry = ministry();
            Member member = member(MemberStatus.ACTIVE);
            when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
            when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
            when(memberMinistryRepository.existsById(new MemberMinistryId(member.getId(), ministry.getId())))
                    .thenReturn(true);

            assertApiException(() -> ministryService.addMember(ministry.getId(),
                    new MinistryMemberRequest(member.getId(), null)), HttpStatus.CONFLICT);
            verify(memberMinistryRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = MemberStatus.class, names = {"INACTIVE", "DECEASED"})
        void rejectsMembersThatCantServe(MemberStatus status) {
            Ministry ministry = ministry();
            Member member = member(status);
            when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
            when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));

            assertApiException(() -> ministryService.addMember(ministry.getId(),
                    new MinistryMemberRequest(member.getId(), null)), HttpStatus.CONFLICT);
            verify(memberMinistryRepository, never()).save(any());
        }
    }

    @Test
    void removeMemberReturnsNotFoundWhenNotAssigned() {
        Ministry ministry = ministry();
        UUID memberId = UUID.randomUUID();
        when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
        when(memberMinistryRepository.findById(new MemberMinistryId(memberId, ministry.getId())))
                .thenReturn(Optional.empty());

        assertApiException(() -> ministryService.removeMember(ministry.getId(), memberId), HttpStatus.NOT_FOUND);
    }

    @Nested
    class Schedules {

        @Test
        void addsSchedule() {
            Ministry ministry = ministry();
            when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
            when(serviceScheduleRepository.save(any(ServiceSchedule.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ServiceScheduleResponse response = ministryService.addSchedule(ministry.getId(),
                    new ServiceScheduleRequest(DayOfWeek.SUNDAY, TEN));

            assertThat(response.dayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            assertThat(response.startTime()).isEqualTo(TEN);
        }

        @Test
        void rejectsDuplicatedSlot() {
            Ministry ministry = ministry();
            when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
            when(serviceScheduleRepository.existsByMinistryIdAndDayOfWeekAndStartTime(
                    ministry.getId(), DayOfWeek.SUNDAY, TEN)).thenReturn(true);

            assertApiException(() -> ministryService.addSchedule(ministry.getId(),
                    new ServiceScheduleRequest(DayOfWeek.SUNDAY, TEN)), HttpStatus.CONFLICT);
            verify(serviceScheduleRepository, never()).save(any());
        }

        @Test
        void returnsNotFoundForScheduleOfAnotherMinistry() {
            Ministry ministry = ministry();
            UUID scheduleId = UUID.randomUUID();
            when(ministryRepository.findById(ministry.getId())).thenReturn(Optional.of(ministry));
            when(serviceScheduleRepository.findByIdAndMinistryId(scheduleId, ministry.getId()))
                    .thenReturn(Optional.empty());

            assertApiException(() -> ministryService.deleteSchedule(ministry.getId(), scheduleId),
                    HttpStatus.NOT_FOUND);
        }
    }

    private static void assertApiException(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(status);
    }

    private static Ministry ministry() {
        Ministry ministry = new Ministry();
        ministry.setId(UUID.randomUUID());
        ministry.setName("Alabanza");
        return ministry;
    }

    private static Member member(MemberStatus status) {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        member.setFirstName("Ana");
        member.setLastName("Pérez");
        member.setStatus(status);
        return member;
    }
}
