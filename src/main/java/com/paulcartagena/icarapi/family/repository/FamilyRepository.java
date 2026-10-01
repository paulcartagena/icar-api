package com.paulcartagena.icarapi.family.repository;

import com.paulcartagena.icarapi.family.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FamilyRepository extends JpaRepository<Family, UUID> {
}
