package com.paulcartagena.icarapi.user.service;

import com.paulcartagena.icarapi.auth.repository.RefreshTokenRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.user.dto.ChangePasswordRequest;
import com.paulcartagena.icarapi.user.dto.CreateUserRequest;
import com.paulcartagena.icarapi.user.dto.ResetPasswordRequest;
import com.paulcartagena.icarapi.user.dto.UpdateUserRequest;
import com.paulcartagena.icarapi.user.dto.UserResponse;
import com.paulcartagena.icarapi.user.entity.AppUser;
import com.paulcartagena.icarapi.user.enums.Permission;
import com.paulcartagena.icarapi.user.enums.UserRole;
import com.paulcartagena.icarapi.user.enums.UserStatus;
import com.paulcartagena.icarapi.user.repository.AppUserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Users are never deleted, only deactivated.
// Anti-escalation: an actor can only manage users of a lower role and assign roles lower than their own.
// USERS_ADMIN (only ADMIN) lifts that limit, so an admin can also manage other admins.
@Service
@Transactional
public class UserService {

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository appUserRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return appUserRepository.findAll(Sort.by("username")).stream()
                .map(this::buildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return buildResponse(findUser(id));
    }

    public UserResponse createUser(CreateUserRequest request, UUID actorId) {
        AppUser actor = findActor(actorId);
        checkCanAssign(actor, request.role());

        String username = request.username().trim();
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.duplicateResource("Username already exists.");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);

        return buildResponse(appUserRepository.save(user));
    }

    public UserResponse updateUser(UUID id, UpdateUserRequest request, UUID actorId) {
        AppUser actor = findActor(actorId);
        AppUser user = findUser(id);
        // Changing your own role would be a self-promotion or a self-demotion
        checkNotSelf(actor, user, "You can't edit your own account here.");
        checkCanManage(actor, user);
        if (request.role() != user.getRole()) {
            checkCanAssign(actor, request.role());
        }

        // New permissions apply on the next refresh, which reads the role from the DB
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        return buildResponse(user);
    }

    public UserResponse activateUser(UUID id, UUID actorId) {
        AppUser user = findManageableUser(id, actorId, "You can't activate your own account.");

        user.setStatus(UserStatus.ACTIVE);
        return buildResponse(user);
    }

    public UserResponse deactivateUser(UUID id, UUID actorId) {
        // Not on yourself: the last admin could lock everyone out
        AppUser user = findManageableUser(id, actorId, "You can't deactivate your own account.");

        user.setStatus(UserStatus.INACTIVE);
        revokeSessions(user.getId());
        return buildResponse(user);
    }

    public void resetPassword(UUID id, ResetPasswordRequest request, UUID actorId) {
        AppUser user = findManageableUser(id, actorId, "Use /users/me/password to change your own password.");

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Whoever had the old password must not keep a session
        revokeSessions(user.getId());
    }

    public void revokeAllSessions(UUID id) {
        revokeSessions(findUser(id).getId());
    }

    public void changeOwnPassword(ChangePasswordRequest request, UUID actorId) {
        AppUser actor = findActor(actorId);

        if (!passwordEncoder.matches(request.currentPassword(), actor.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect.");
        }

        actor.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Logs out every device, including this one once its access token expires
        revokeSessions(actor.getId());
    }

    private AppUser findManageableUser(UUID id, UUID actorId, String selfMessage) {
        AppUser actor = findActor(actorId);
        AppUser user = findUser(id);
        checkNotSelf(actor, user, selfMessage);
        checkCanManage(actor, user);
        return user;
    }

    private void checkCanManage(AppUser actor, AppUser user) {
        if (!actor.getRole().outranks(user.getRole()) && !actor.getRole().hasPermission(Permission.USERS_ADMIN)) {
            throw ApiException.forbidden("You can't manage users with the same or a higher role than yours.");
        }
    }

    private void checkCanAssign(AppUser actor, UserRole role) {
        if (!actor.getRole().outranks(role) && !actor.getRole().hasPermission(Permission.USERS_ADMIN)) {
            throw ApiException.forbidden("You can't assign the " + role + " role.");
        }
    }

    private void checkNotSelf(AppUser actor, AppUser user, String message) {
        if (actor.getId().equals(user.getId())) {
            throw ApiException.badRequest(message);
        }
    }

    private void revokeSessions(UUID userId) {
        refreshTokenRepository.revokeAllActiveByUserId(userId, LocalDateTime.now());
    }

    private AppUser findUser(UUID id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("User not found: " + id));
    }

    // The role is read from the DB, not the token: a demotion takes effect right away here
    private AppUser findActor(UUID actorId) {
        return appUserRepository.findById(actorId)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists."));
    }

    private UserResponse buildResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.getStatus()
        );
    }
}
