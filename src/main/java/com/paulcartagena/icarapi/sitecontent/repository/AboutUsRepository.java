package com.paulcartagena.icarapi.sitecontent.repository;

import com.paulcartagena.icarapi.sitecontent.entity.AboutUs;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Single-row table (V16), so the first row is the only one
public interface AboutUsRepository extends JpaRepository<AboutUs, UUID> {

    Optional<AboutUs> findFirstByOrderByIdAsc();
}
