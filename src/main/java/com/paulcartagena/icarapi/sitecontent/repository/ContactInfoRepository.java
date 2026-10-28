package com.paulcartagena.icarapi.sitecontent.repository;

import com.paulcartagena.icarapi.sitecontent.entity.ContactInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Single-row table (V16), so the first row is the only one
public interface ContactInfoRepository extends JpaRepository<ContactInfo, UUID> {

    Optional<ContactInfo> findFirstByOrderByIdAsc();
}
