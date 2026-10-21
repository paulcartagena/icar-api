package com.paulcartagena.icarapi.donation.repository;

import com.paulcartagena.icarapi.donation.entity.Donation;
import com.paulcartagena.icarapi.donation.enums.DonationMethod;
import com.paulcartagena.icarapi.donation.enums.DonationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DonationRepository extends JpaRepository<Donation, UUID>, JpaSpecificationExecutor<Donation> {

    List<Donation> findByMethod(DonationMethod method);
    List<Donation> findByStatus(DonationStatus status);
    Optional<Donation> findByPaypalOrderId(String paypalOrderId);
}
