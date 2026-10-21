package com.paulcartagena.icarapi.donation.controller;

import com.paulcartagena.icarapi.donation.dto.DonationRequest;
import com.paulcartagena.icarapi.donation.dto.DonationResponse;
import com.paulcartagena.icarapi.donation.dto.DonationVoidRequest;
import com.paulcartagena.icarapi.donation.enums.DonationMethod;
import com.paulcartagena.icarapi.donation.enums.DonationStatus;
import com.paulcartagena.icarapi.donation.service.DonationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/donations")
@Tag(name = "Donations")
public class DonationController {

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @GetMapping
    public List<DonationResponse> getAllDonations(@RequestParam(required = false) DonationStatus status,
                                                  @RequestParam(required = false) DonationMethod method,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return donationService.getAllDonations(status, method, from, to);
    }

    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @GetMapping("/{id}")
    public DonationResponse getDonationById(@PathVariable UUID id) {
        return donationService.getDonationById(id);
    }

    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DonationResponse createDonation(@Valid @RequestBody DonationRequest donationRequest,
                                           @AuthenticationPrincipal Jwt jwt) {
        return donationService.createDonation(donationRequest, UUID.fromString(jwt.getSubject()));
    }

    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    @PatchMapping("/{id}/void")
    public DonationResponse voidDonation(@PathVariable UUID id,
                                         @Valid @RequestBody DonationVoidRequest donationVoidRequest) {
        return donationService.voidDonation(id, donationVoidRequest);
    }
}
