package com.paulcartagena.icarapi.visitrequest.controller;

import com.paulcartagena.icarapi.visitrequest.dto.VisitRequestResponse;
import com.paulcartagena.icarapi.visitrequest.service.VisitRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Admin side of the visit requests: personal data for pastoral follow-up, so it uses the membership permissions
@RestController
@RequestMapping("/visit-requests")
@Tag(name = "Visit requests")
public class VisitRequestController {

    private final VisitRequestService visitRequestService;

    public VisitRequestController(VisitRequestService visitRequestService) {
        this.visitRequestService = visitRequestService;
    }

    @PreAuthorize("hasAuthority('MEMBERS_READ')")
    @GetMapping
    public List<VisitRequestResponse> getAllVisitRequests() {
        return visitRequestService.getAllVisitRequests();
    }

    @PreAuthorize("hasAuthority('MEMBERS_READ')")
    @GetMapping("/{id}")
    public VisitRequestResponse getVisitRequestById(@PathVariable UUID id) {
        return visitRequestService.getVisitRequestById(id);
    }
}
