package com.paulcartagena.icarapi.sitecontent.repository;

import com.paulcartagena.icarapi.sitecontent.entity.ContactInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContactInfoRepository extends JpaRepository<ContactInfo, UUID> {
}
