package com.paulcartagena.icarapi.visitrequest.repository;

import com.paulcartagena.icarapi.visitrequest.entity.VisitRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VisitRequestRepository extends JpaRepository<VisitRequest, UUID> {

    List<VisitRequest> findAllByOrderByCreatedAtDesc();
}
