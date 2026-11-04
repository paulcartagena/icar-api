package com.paulcartagena.icarapi.visitrequest.service;

import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.visitrequest.dto.VisitRequestRequest;
import com.paulcartagena.icarapi.visitrequest.dto.VisitRequestResponse;
import com.paulcartagena.icarapi.visitrequest.entity.VisitRequest;
import com.paulcartagena.icarapi.visitrequest.repository.VisitRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitRequestServiceTest {

    @Mock
    private VisitRequestRepository visitRequestRepository;

    @InjectMocks
    private VisitRequestService visitRequestService;

    @Test
    void createTrimsTheFields() {
        when(visitRequestRepository.save(any(VisitRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VisitRequestResponse response = visitRequestService.createVisitRequest(new VisitRequestRequest(
                " Ana Pérez ", " +507 6000-0000 ", " ana@example.com ", " Quiero conocer la iglesia "));

        ArgumentCaptor<VisitRequest> saved = ArgumentCaptor.forClass(VisitRequest.class);
        verify(visitRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(response.fullName()).isEqualTo("Ana Pérez");
        assertThat(response.phone()).isEqualTo("+507 6000-0000");
        assertThat(response.email()).isEqualTo("ana@example.com");
        assertThat(response.message()).isEqualTo("Quiero conocer la iglesia");
    }

    @Test
    void blankMessageIsStoredAsNull() {
        when(visitRequestRepository.save(any(VisitRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VisitRequestResponse response = visitRequestService.createVisitRequest(
                new VisitRequestRequest("Ana Pérez", "60000000", "ana@example.com", "   "));

        assertThat(response.message()).isNull();
    }

    @Test
    void listReturnsTheRequestsNewestFirst() {
        VisitRequest newer = visitRequest("Ana", LocalDateTime.of(2026, 11, 3, 10, 0));
        VisitRequest older = visitRequest("Luis", LocalDateTime.of(2026, 11, 1, 9, 0));
        when(visitRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(newer, older));

        List<VisitRequestResponse> responses = visitRequestService.getAllVisitRequests();

        assertThat(responses).extracting(VisitRequestResponse::fullName).containsExactly("Ana", "Luis");
    }

    @Test
    void getByIdReturnsTheRequest() {
        VisitRequest visitRequest = visitRequest("Ana", LocalDateTime.of(2026, 11, 3, 10, 0));
        when(visitRequestRepository.findById(visitRequest.getId())).thenReturn(Optional.of(visitRequest));

        VisitRequestResponse response = visitRequestService.getVisitRequestById(visitRequest.getId());

        assertThat(response.id()).isEqualTo(visitRequest.getId());
        assertThat(response.createdAt()).isEqualTo(visitRequest.getCreatedAt());
    }

    @Test
    void getByIdOfAMissingRequestReturns404() {
        UUID id = UUID.randomUUID();
        when(visitRequestRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> visitRequestService.getVisitRequestById(id))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static VisitRequest visitRequest(String fullName, LocalDateTime createdAt) {
        return new VisitRequest(UUID.randomUUID(), fullName, "60000000", "visit@example.com", null, createdAt);
    }
}
