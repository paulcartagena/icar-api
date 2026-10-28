package com.paulcartagena.icarapi.sitecontent.service;

import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.sitecontent.dto.AboutUsRequest;
import com.paulcartagena.icarapi.sitecontent.dto.AboutUsResponse;
import com.paulcartagena.icarapi.sitecontent.dto.ContactInfoRequest;
import com.paulcartagena.icarapi.sitecontent.dto.ContactInfoResponse;
import com.paulcartagena.icarapi.sitecontent.entity.AboutUs;
import com.paulcartagena.icarapi.sitecontent.entity.ContactInfo;
import com.paulcartagena.icarapi.sitecontent.repository.AboutUsRepository;
import com.paulcartagena.icarapi.sitecontent.repository.ContactInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "About us" and contact info are single records: PUT creates them the first time and updates them afterwards
@Service
@Transactional
public class SiteContentService {

    private final AboutUsRepository aboutUsRepository;
    private final ContactInfoRepository contactInfoRepository;

    public SiteContentService(AboutUsRepository aboutUsRepository, ContactInfoRepository contactInfoRepository) {
        this.aboutUsRepository = aboutUsRepository;
        this.contactInfoRepository = contactInfoRepository;
    }

    // --- About us ---

    @Transactional(readOnly = true)
    public AboutUsResponse getAboutUs() {
        return aboutUsRepository.findFirstByOrderByIdAsc()
                .map(this::buildAboutUsResponse)
                .orElseThrow(() -> ApiException.resourceNotFound("About us has not been configured yet."));
    }

    public AboutUsResponse saveAboutUs(AboutUsRequest request) {
        AboutUs aboutUs = aboutUsRepository.findFirstByOrderByIdAsc().orElseGet(AboutUs::new);

        aboutUs.setTitle(request.title().trim());
        aboutUs.setBody(request.body().trim());
        aboutUs.setImageUrl(request.imageUrl().trim());

        AboutUs savedAboutUs = aboutUsRepository.save(aboutUs);
        return buildAboutUsResponse(savedAboutUs);
    }

    // --- Contact info ---

    @Transactional(readOnly = true)
    public ContactInfoResponse getContactInfo() {
        return contactInfoRepository.findFirstByOrderByIdAsc()
                .map(this::buildContactInfoResponse)
                .orElseThrow(() -> ApiException.resourceNotFound("Contact info has not been configured yet."));
    }

    public ContactInfoResponse saveContactInfo(ContactInfoRequest request) {
        ContactInfo contactInfo = contactInfoRepository.findFirstByOrderByIdAsc().orElseGet(ContactInfo::new);

        contactInfo.setAddress(request.address().trim());
        contactInfo.setPhone(request.phone().trim());
        contactInfo.setEmail(request.email().trim());
        contactInfo.setMapUrl(request.mapUrl().trim());

        ContactInfo savedContactInfo = contactInfoRepository.save(contactInfo);
        return buildContactInfoResponse(savedContactInfo);
    }

    private AboutUsResponse buildAboutUsResponse(AboutUs aboutUs) {
        return new AboutUsResponse(aboutUs.getTitle(), aboutUs.getBody(), aboutUs.getImageUrl());
    }

    private ContactInfoResponse buildContactInfoResponse(ContactInfo contactInfo) {
        return new ContactInfoResponse(
                contactInfo.getAddress(),
                contactInfo.getPhone(),
                contactInfo.getEmail(),
                contactInfo.getMapUrl()
        );
    }
}
