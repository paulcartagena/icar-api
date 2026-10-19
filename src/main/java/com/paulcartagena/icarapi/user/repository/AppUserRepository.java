package com.paulcartagena.icarapi.user.repository;

import com.paulcartagena.icarapi.user.entity.AppUser;
import com.paulcartagena.icarapi.user.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByRole(UserRole role);
}
