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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class MinistryService {

    // Members in these statuses can't be assigned to a ministry
    private static final Set<MemberStatus> NOT_ASSIGNABLE = Set.of(MemberStatus.INACTIVE, MemberStatus.DECEASED);

    private final MinistryRepository ministryRepository;
    private final ServiceScheduleRepository serviceScheduleRepository;
    private final MemberRepository memberRepository;
    private final MemberMinistryRepository memberMinistryRepository;

    public MinistryService(MinistryRepository ministryRepository,
                           ServiceScheduleRepository serviceScheduleRepository,
                           MemberRepository memberRepository,
                           MemberMinistryRepository memberMinistryRepository) {
        this.ministryRepository = ministryRepository;
        this.serviceScheduleRepository = serviceScheduleRepository;
        this.memberRepository = memberRepository;
        this.memberMinistryRepository = memberMinistryRepository;
    }

    @Transactional(readOnly = true)
    public List<MinistryResponse> getAllMinistries() {
        return ministryRepository.findAll(Sort.by("name")).stream()
                .map(this::buildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MinistryResponse getMinistryById(UUID id) {
        return buildResponse(findMinistry(id));
    }

    public MinistryResponse createMinistry(MinistryRequest ministryRequest) {
        String name = ministryRequest.name().trim();

        // Validation: unique (case-insensitive)
        if (ministryRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.duplicateResource("Ministry already exists.");
        }

        Ministry ministry = new Ministry();
        ministry.setName(name);
        ministry.setDescription(blankToNull(ministryRequest.description()));

        Ministry savedMinistry = ministryRepository.save(ministry);
        return buildResponse(savedMinistry);
    }

    public MinistryResponse updateMinistry(UUID id, MinistryRequest ministryRequest) {
        Ministry ministry = findMinistry(id);
        String name = ministryRequest.name().trim();

        // Validation: unique (case-insensitive), excluding the ministry itself
        if (ministryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.duplicateResource("Ministry already exists.");
        }

        ministry.setName(name);
        ministry.setDescription(blankToNull(ministryRequest.description()));
        return buildResponse(ministry);
    }

    // Schedules and member assignments go with it (cascade); the members themselves are kept
    public void deleteMinistry(UUID id) {
        ministryRepository.delete(findMinistry(id));
    }

    // --- Members of the ministry ---

    @Transactional(readOnly = true)
    public List<MinistryMemberResponse> getMinistryMembers(UUID ministryId) {
        return findMinistry(ministryId).getMembers().stream()
                .sorted(Comparator.comparing((MemberMinistry mm) -> mm.getMember().getLastName())
                        .thenComparing(mm -> mm.getMember().getFirstName()))
                .map(this::buildMemberResponse)
                .toList();
    }

    public MinistryMemberResponse addMember(UUID ministryId, MinistryMemberRequest request) {
        Ministry ministry = findMinistry(ministryId);
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> ApiException.resourceNotFound("Member not found: " + request.memberId()));

        if (NOT_ASSIGNABLE.contains(member.getStatus())) {
            throw ApiException.conflict("Member with status " + member.getStatus() + " can't join a ministry.");
        }
        MemberMinistryId id = new MemberMinistryId(member.getId(), ministryId);
        if (memberMinistryRepository.existsById(id)) {
            throw ApiException.duplicateResource("Member already belongs to this ministry.");
        }

        MemberMinistry memberMinistry = new MemberMinistry();
        memberMinistry.setId(id);
        memberMinistry.setMember(member);
        memberMinistry.setMinistry(ministry);
        memberMinistry.setJoinedAt(request.joinedAt() != null ? request.joinedAt() : LocalDateTime.now());

        MemberMinistry saved = memberMinistryRepository.save(memberMinistry);
        return buildMemberResponse(saved);
    }

    public void removeMember(UUID ministryId, UUID memberId) {
        findMinistry(ministryId);
        MemberMinistry memberMinistry = memberMinistryRepository.findById(new MemberMinistryId(memberId, ministryId))
                .orElseThrow(() -> ApiException.resourceNotFound("Member does not belong to this ministry."));

        memberMinistryRepository.delete(memberMinistry);
    }

    // --- Service schedules ---

    public ServiceScheduleResponse addSchedule(UUID ministryId, ServiceScheduleRequest request) {
        Ministry ministry = findMinistry(ministryId);

        if (serviceScheduleRepository.existsByMinistryIdAndDayOfWeekAndStartTime(
                ministryId, request.dayOfWeek(), request.startTime())) {
            throw ApiException.duplicateResource("Ministry already has a schedule at that day and time.");
        }

        ServiceSchedule schedule = new ServiceSchedule();
        schedule.setMinistry(ministry);
        schedule.setDayOfWeek(request.dayOfWeek());
        schedule.setStartTime(request.startTime());

        ServiceSchedule saved = serviceScheduleRepository.save(schedule);
        return buildScheduleResponse(saved);
    }

    public ServiceScheduleResponse updateSchedule(UUID ministryId, UUID scheduleId, ServiceScheduleRequest request) {
        ServiceSchedule schedule = findSchedule(ministryId, scheduleId);

        if (serviceScheduleRepository.existsByMinistryIdAndDayOfWeekAndStartTimeAndIdNot(
                ministryId, request.dayOfWeek(), request.startTime(), scheduleId)) {
            throw ApiException.duplicateResource("Ministry already has a schedule at that day and time.");
        }

        schedule.setDayOfWeek(request.dayOfWeek());
        schedule.setStartTime(request.startTime());
        return buildScheduleResponse(schedule);
    }

    public void deleteSchedule(UUID ministryId, UUID scheduleId) {
        serviceScheduleRepository.delete(findSchedule(ministryId, scheduleId));
    }

    private Ministry findMinistry(UUID id) {
        return ministryRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Ministry not found: " + id));
    }

    private ServiceSchedule findSchedule(UUID ministryId, UUID scheduleId) {
        findMinistry(ministryId);
        return serviceScheduleRepository.findByIdAndMinistryId(scheduleId, ministryId)
                .orElseThrow(() -> ApiException.resourceNotFound("Schedule not found: " + scheduleId));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private MinistryResponse buildResponse(Ministry ministry) {
        List<ServiceScheduleResponse> schedules = ministry.getServices().stream()
                .sorted(Comparator.comparing(ServiceSchedule::getDayOfWeek)
                        .thenComparing(ServiceSchedule::getStartTime))
                .map(this::buildScheduleResponse)
                .toList();

        return new MinistryResponse(
                ministry.getId(),
                ministry.getName(),
                ministry.getDescription(),
                ministry.getMembers().size(),
                schedules
        );
    }

    private ServiceScheduleResponse buildScheduleResponse(ServiceSchedule schedule) {
        return new ServiceScheduleResponse(schedule.getId(), schedule.getDayOfWeek(), schedule.getStartTime());
    }

    private MinistryMemberResponse buildMemberResponse(MemberMinistry memberMinistry) {
        Member member = memberMinistry.getMember();
        return new MinistryMemberResponse(
                member.getId(),
                member.getFirstName(),
                member.getLastName(),
                member.getStatus(),
                memberMinistry.getJoinedAt()
        );
    }
}
