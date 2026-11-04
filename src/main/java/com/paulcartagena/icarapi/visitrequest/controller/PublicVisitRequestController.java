package com.paulcartagena.icarapi.visitrequest.controller;

import com.paulcartagena.icarapi.visitrequest.dto.VisitRequestRequest;
import com.paulcartagena.icarapi.visitrequest.dto.VisitRequestResponse;
import com.paulcartagena.icarapi.visitrequest.service.VisitRequestService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// "Planea tu visita" form of the public website: no token needed (PUBLIC_ROUTES in SecurityConfig),
// rate limited per client IP against spam
@RestController
@RequestMapping("/public/visit-requests")
@Tag(name = "Public")
@SecurityRequirements
public class PublicVisitRequestController {

    private final VisitRequestService visitRequestService;

    public PublicVisitRequestController(VisitRequestService visitRequestService) {
        this.visitRequestService = visitRequestService;
    }

    @PreAuthorize("permitAll()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisitRequestResponse createVisitRequest(@Valid @RequestBody VisitRequestRequest request) {
        return visitRequestService.createVisitRequest(request);
    }
}
