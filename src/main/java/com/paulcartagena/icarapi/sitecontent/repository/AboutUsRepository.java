package com.paulcartagena.icarapi.sitecontent.repository;

import com.paulcartagena.icarapi.sitecontent.entity.AboutUs;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AboutUsRepository extends JpaRepository<AboutUs, UUID> {
}
