package com.paulcartagena.icarapi.user.controller;

import com.paulcartagena.icarapi.user.dto.ChangePasswordRequest;
import com.paulcartagena.icarapi.user.dto.CreateUserRequest;
import com.paulcartagena.icarapi.user.dto.ResetPasswordRequest;
import com.paulcartagena.icarapi.user.dto.UpdateUserRequest;
import com.paulcartagena.icarapi.user.dto.UserResponse;
import com.paulcartagena.icarapi.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest createUserRequest,
                                   @AuthenticationPrincipal Jwt jwt) {
        return userService.createUser(createUserRequest, actorId(jwt));
    }

    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateUserRequest updateUserRequest,
                                   @AuthenticationPrincipal Jwt jwt) {
        return userService.updateUser(id, updateUserRequest, actorId(jwt));
    }

    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @PatchMapping("/{id}/activate")
    public UserResponse activateUser(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return userService.activateUser(id, actorId(jwt));
    }

    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @PatchMapping("/{id}/deactivate")
    public UserResponse deactivateUser(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return userService.deactivateUser(id, actorId(jwt));
    }

    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id,
                              @Valid @RequestBody ResetPasswordRequest resetPasswordRequest,
                              @AuthenticationPrincipal Jwt jwt) {
        userService.resetPassword(id, resetPasswordRequest, actorId(jwt));
    }

    @PreAuthorize("hasAuthority('USERS_ADMIN')")
    @PostMapping("/{id}/revoke-sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(@PathVariable UUID id) {
        userService.revokeAllSessions(id);
    }

    // Any logged-in user, whatever their role
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
                                  @AuthenticationPrincipal Jwt jwt) {
        userService.changeOwnPassword(changePasswordRequest, actorId(jwt));
    }

    private static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
