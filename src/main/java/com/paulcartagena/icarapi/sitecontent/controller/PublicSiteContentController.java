package com.paulcartagena.icarapi.sitecontent.controller;

import com.paulcartagena.icarapi.sitecontent.dto.AboutUsResponse;
import com.paulcartagena.icarapi.sitecontent.dto.ContactInfoResponse;
import com.paulcartagena.icarapi.sitecontent.service.SiteContentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Read-only site content for the public website, no token needed (GET /public/** in SecurityConfig)
@RestController
@RequestMapping("/public")
@Tag(name = "Public")
public class PublicSiteContentController {

    private final SiteContentService siteContentService;

    public PublicSiteContentController(SiteContentService siteContentService) {
        this.siteContentService = siteContentService;
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/about-us")
    public AboutUsResponse getAboutUs() {
        return siteContentService.getAboutUs();
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/contact-info")
    public ContactInfoResponse getContactInfo() {
        return siteContentService.getContactInfo();
    }
}
