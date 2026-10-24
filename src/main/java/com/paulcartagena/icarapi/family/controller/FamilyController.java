package com.paulcartagena.icarapi.family.controller;

import com.paulcartagena.icarapi.family.dto.FamilyRequest;
import com.paulcartagena.icarapi.family.dto.FamilyResponse;
import com.paulcartagena.icarapi.family.service.FamilyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/families")
@Tag(name = "Families")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PreAuthorize("hasAuthority('MEMBERS_READ')")
    @GetMapping
    public List<FamilyResponse> getAllFamilies() {
        return familyService.getAllFamilies();
    }

    @PreAuthorize("hasAuthority('MEMBERS_READ')")
    @GetMapping("/{id}")
    public FamilyResponse getFamilyById(@PathVariable UUID id) {
        return familyService.getFamilyById(id);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyResponse createFamily(@Valid @RequestBody FamilyRequest familyRequest) {
        return familyService.createFamily(familyRequest);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @PutMapping("/{id}")
    public FamilyResponse updateFamily(@PathVariable UUID id, @Valid @RequestBody FamilyRequest familyRequest) {
        return familyService.updateFamily(id, familyRequest);
    }

    @PreAuthorize("hasAuthority('MEMBERS_WRITE')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFamily(@PathVariable UUID id) {
        familyService.deleteFamily(id);
    }
}
