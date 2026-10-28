package com.paulcartagena.icarapi.sitecontent.controller;

import com.paulcartagena.icarapi.sitecontent.dto.AboutUsRequest;
import com.paulcartagena.icarapi.sitecontent.dto.AboutUsResponse;
import com.paulcartagena.icarapi.sitecontent.dto.ContactInfoRequest;
import com.paulcartagena.icarapi.sitecontent.dto.ContactInfoResponse;
import com.paulcartagena.icarapi.sitecontent.service.SiteContentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Admin side of the site content; the panel reads it through the public GETs (same data, nothing hidden)
@RestController
@RequestMapping("/site-content")
@Tag(name = "Site content")
public class SiteContentController {

    private final SiteContentService siteContentService;

    public SiteContentController(SiteContentService siteContentService) {
        this.siteContentService = siteContentService;
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @PutMapping("/about-us")
    public AboutUsResponse saveAboutUs(@Valid @RequestBody AboutUsRequest request) {
        return siteContentService.saveAboutUs(request);
    }

    @PreAuthorize("hasAuthority('CONTENT_WRITE')")
    @PutMapping("/contact-info")
    public ContactInfoResponse saveContactInfo(@Valid @RequestBody ContactInfoRequest request) {
        return siteContentService.saveContactInfo(request);
    }
}
