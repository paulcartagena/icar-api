package com.paulcartagena.icarapi.ministry.repository;

import com.paulcartagena.icarapi.ministry.entity.Ministry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MinistryRepository extends JpaRepository<Ministry, UUID> {

    // Used to create non-duplicated ministries
    boolean existsByNameIgnoreCase(String name);

    // Used on update: same name, excluding the ministry being edited
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
