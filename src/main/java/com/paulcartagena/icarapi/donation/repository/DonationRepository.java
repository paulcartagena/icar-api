package com.paulcartagena.icarapi.donation.repository;

import com.paulcartagena.icarapi.donation.entity.Donation;
import com.paulcartagena.icarapi.donation.enums.DonationMethod;
import com.paulcartagena.icarapi.donation.enums.DonationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DonationRepository extends JpaRepository<Donation, UUID>, JpaSpecificationExecutor<Donation> {

    List<Donation> findByMethod(DonationMethod method);
    List<Donation> findByStatus(DonationStatus status);
    Optional<Donation> findByPaypalOrderId(String paypalOrderId);

    // Locks the row so the capture call and the PayPal webhook can't complete the same donation at the same time
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Donation> findWithLockByPaypalOrderId(String paypalOrderId);
}
