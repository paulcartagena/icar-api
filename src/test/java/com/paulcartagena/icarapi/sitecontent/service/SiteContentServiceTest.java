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
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteContentServiceTest {

    @Mock
    private AboutUsRepository aboutUsRepository;

    @Mock
    private ContactInfoRepository contactInfoRepository;

    @InjectMocks
    private SiteContentService siteContentService;

    @Nested
    class AboutUsContent {

        @Test
        void firstSaveCreatesTheRecordWithTrimmedFields() {
            when(aboutUsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
            when(aboutUsRepository.save(any(AboutUs.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AboutUsResponse response = siteContentService.saveAboutUs(
                    new AboutUsRequest(" Quiénes somos ", " Somos una iglesia ", " https://cdn.icar.org/a.jpg "));

            ArgumentCaptor<AboutUs> saved = ArgumentCaptor.forClass(AboutUs.class);
            verify(aboutUsRepository).save(saved.capture());
            assertThat(saved.getValue().getId()).isNull();
            assertThat(response.title()).isEqualTo("Quiénes somos");
            assertThat(response.body()).isEqualTo("Somos una iglesia");
            assertThat(response.imageUrl()).isEqualTo("https://cdn.icar.org/a.jpg");
        }

        @Test
        void laterSavesUpdateTheExistingRecord() {
            AboutUs existing = new AboutUs(UUID.randomUUID(), "Antes", "Antes", "https://cdn.icar.org/old.jpg");
            when(aboutUsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
            when(aboutUsRepository.save(any(AboutUs.class))).thenAnswer(invocation -> invocation.getArgument(0));

            siteContentService.saveAboutUs(new AboutUsRequest("Ahora", "Ahora", "https://cdn.icar.org/new.jpg"));

            verify(aboutUsRepository).save(existing);
            assertThat(existing.getTitle()).isEqualTo("Ahora");
            assertThat(existing.getImageUrl()).isEqualTo("https://cdn.icar.org/new.jpg");
        }

        @Test
        void notConfiguredYetReturns404() {
            when(aboutUsRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

            assertApiException(() -> siteContentService.getAboutUs(), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class ContactInfoContent {

        @Test
        void firstSaveCreatesTheRecordWithTrimmedFields() {
            when(contactInfoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
            when(contactInfoRepository.save(any(ContactInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ContactInfoResponse response = siteContentService.saveContactInfo(new ContactInfoRequest(
                    " Calle 1 ", " 555-1234 ", " info@icar.org ", " https://maps.example.com/icar "));

            assertThat(response).isEqualTo(new ContactInfoResponse(
                    "Calle 1", "555-1234", "info@icar.org", "https://maps.example.com/icar"));
        }

        @Test
        void laterSavesUpdateTheExistingRecord() {
            ContactInfo existing = new ContactInfo(UUID.randomUUID(), "Antes", "1", "a@icar.org", "https://maps.example.com/a");
            when(contactInfoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
            when(contactInfoRepository.save(any(ContactInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

            siteContentService.saveContactInfo(
                    new ContactInfoRequest("Calle 2", "2", "b@icar.org", "https://maps.example.com/b"));

            verify(contactInfoRepository).save(existing);
            assertThat(existing.getAddress()).isEqualTo("Calle 2");
            assertThat(existing.getEmail()).isEqualTo("b@icar.org");
        }

        @Test
        void notConfiguredYetReturns404() {
            when(contactInfoRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

            assertApiException(() -> siteContentService.getContactInfo(), HttpStatus.NOT_FOUND);
        }
    }

    private static void assertApiException(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(status);
    }
}
