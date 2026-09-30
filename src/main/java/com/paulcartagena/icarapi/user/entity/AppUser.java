package com.paulcartagena.icarapi.user.entity;

import com.paulcartagena.icarapi.user.enums.UserRole;
import com.paulcartagena.icarapi.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 25, nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "full_name", length = 250, nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(length = 25, nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 25, nullable = false)
    private UserStatus status;
}
