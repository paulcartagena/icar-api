package com.paulcartagena.icarapi.ministry.controller;

import com.paulcartagena.icarapi.ministry.dto.*;
import com.paulcartagena.icarapi.ministry.service.MinistryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ministries")
@Tag(name = "Ministries")
public class MinistryController {

    private final MinistryService ministryService;

    public MinistryController(MinistryService ministryService) {
        this.ministryService = ministryService;
    }

    @PreAuthorize("hasAuthority('MEMBERS_READ')")
    @GetMapping
    public List<MinistryResponse> getAllMinistries() {
        return ministryService.getAllMinistries();
    }

    @PreAuthorize("hasAuthority('MEMBERS_READ')")
    @GetMapping("/{id}")
    public MinistryResponse getMinistryById(@PathVariable UUID id) {
        return ministryService.getMinistryById(id);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MinistryResponse createMinistry(@Valid @RequestBody MinistryRequest ministryRequest) {
        return ministryService.createMinistry(ministryRequest);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @PutMapping("/{id}")
    public MinistryResponse updateMinistry(@PathVariable UUID id, @Valid @RequestBody MinistryRequest ministryRequest) {
        return ministryService.updateMinistry(id, ministryRequest);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMinistry(@PathVariable UUID id) {
        ministryService.deleteMinistry(id);
    }

    @PreAuthorize("hasAuthority('MEMBERS_READ')")
    @GetMapping("/{id}/members")
    public List<MinistryMemberResponse> getMinistryMembers(@PathVariable UUID id) {
        return ministryService.getMinistryMembers(id);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MinistryMemberResponse addMember(@PathVariable UUID id, @Valid @RequestBody MinistryMemberRequest request) {
        return ministryService.addMember(id, request);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @DeleteMapping("/{id}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID id, @PathVariable UUID memberId) {
        ministryService.removeMember(id, memberId);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @PostMapping("/{id}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceScheduleResponse addSchedule(@PathVariable UUID id, @Valid @RequestBody ServiceScheduleRequest request) {
        return ministryService.addSchedule(id, request);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @PutMapping("/{id}/schedules/{scheduleId}")
    public ServiceScheduleResponse updateSchedule(@PathVariable UUID id, @PathVariable UUID scheduleId,
                                                  @Valid @RequestBody ServiceScheduleRequest request) {
        return ministryService.updateSchedule(id, scheduleId, request);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @DeleteMapping("/{id}/schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(@PathVariable UUID id, @PathVariable UUID scheduleId) {
        ministryService.deleteSchedule(id, scheduleId);
    }
}
