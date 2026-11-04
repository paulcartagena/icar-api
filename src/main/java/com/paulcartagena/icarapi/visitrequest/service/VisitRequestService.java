package com.paulcartagena.icarapi.visitrequest.service;

import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.visitrequest.dto.VisitRequestRequest;
import com.paulcartagena.icarapi.visitrequest.dto.VisitRequestResponse;
import com.paulcartagena.icarapi.visitrequest.entity.VisitRequest;
import com.paulcartagena.icarapi.visitrequest.repository.VisitRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class VisitRequestService {

    private final VisitRequestRepository visitRequestRepository;

    public VisitRequestService(VisitRequestRepository visitRequestRepository) {
        this.visitRequestRepository = visitRequestRepository;
    }

    // --- Public site ---

    public VisitRequestResponse createVisitRequest(VisitRequestRequest request) {
        VisitRequest visitRequest = new VisitRequest();
        visitRequest.setFullName(request.fullName().trim());
        visitRequest.setPhone(request.phone().trim());
        visitRequest.setEmail(request.email().trim());
        visitRequest.setMessage(blankToNull(request.message()));

        return buildResponse(visitRequestRepository.save(visitRequest));
    }

    // --- Admin ---

    @Transactional(readOnly = true)
    public List<VisitRequestResponse> getAllVisitRequests() {
        return visitRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::buildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VisitRequestResponse getVisitRequestById(UUID id) {
        return visitRequestRepository.findById(id)
                .map(this::buildResponse)
                .orElseThrow(() -> ApiException.resourceNotFound("Visit request not found: " + id));
    }

    // --- Helpers ---

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private VisitRequestResponse buildResponse(VisitRequest visitRequest) {
        return new VisitRequestResponse(
                visitRequest.getId(),
                visitRequest.getFullName(),
                visitRequest.getPhone(),
                visitRequest.getEmail(),
                visitRequest.getMessage(),
                visitRequest.getCreatedAt()
        );
    }
}
