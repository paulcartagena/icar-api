package com.paulcartagena.icarapi.ministry.controller;

import com.paulcartagena.icarapi.ministry.dto.PublicMinistryResponse;
import com.paulcartagena.icarapi.ministry.service.MinistryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Read-only ministries and their service schedules for the public website, no token needed
// (GET /public/** in SecurityConfig)
@RestController
@RequestMapping("/public/ministries")
@Tag(name = "Public")
public class PublicMinistryController {

    private final MinistryService ministryService;

    public PublicMinistryController(MinistryService ministryService) {
        this.ministryService = ministryService;
    }

    @PreAuthorize("permitAll()")
    @GetMapping
    public List<PublicMinistryResponse> getMinistries() {
        return ministryService.getPublicMinistries();
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public PublicMinistryResponse getMinistryById(@PathVariable UUID id) {
        return ministryService.getPublicMinistryById(id);
    }
}
