package com.paulcartagena.icarapi.family.repository;

import com.paulcartagena.icarapi.family.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FamilyRepository extends JpaRepository<Family, UUID> {

    // A member can be responsible for one family only (V2 uq_family_responsible_member)
    boolean existsByResponsibleMemberId(UUID memberId);
}
