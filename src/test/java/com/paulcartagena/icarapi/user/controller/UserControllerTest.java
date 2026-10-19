package com.paulcartagena.icarapi.user.controller;

import com.paulcartagena.icarapi.config.SecurityConfig;
import com.paulcartagena.icarapi.user.enums.Permission;
import com.paulcartagena.icarapi.user.enums.UserRole;
import com.paulcartagena.icarapi.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Endpoint-level permissions; the role hierarchy rules are covered in UserServiceTest
@WebMvcTest(controllers = UserController.class, properties = "jwt.secret=test-secret-with-at-least-32-characters!!")
@Import(SecurityConfig.class)
class UserControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PASSWORD_CHANGE = """
            {"currentPassword": "current-password", "newPassword": "a-strong-password"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // A token with the role's real permissions and a UUID subject
    private static JwtRequestPostProcessor as(UserRole role) {
        SimpleGrantedAuthority[] authorities = role.getPermissions().stream()
                .map(Permission::name)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(jwt -> jwt.subject(USER_ID.toString())).authorities(authorities);
    }

    @Test
    void treasurerCannotListUsers() throws Exception {
        mockMvc.perform(get("/users").with(as(UserRole.TREASURER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pastorCanListUsers() throws Exception {
        mockMvc.perform(get("/users").with(as(UserRole.PASTOR)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminCanRevokeSessions() throws Exception {
        UUID target = UUID.randomUUID();

        mockMvc.perform(post("/users/" + target + "/revoke-sessions").with(as(UserRole.PASTOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/users/" + target + "/revoke-sessions").with(as(UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        verify(userService).revokeAllSessions(target);
    }

    @Test
    void anyRoleCanChangeTheirOwnPassword() throws Exception {
        mockMvc.perform(put("/users/me/password").with(as(UserRole.TREASURER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_CHANGE))
                .andExpect(status().isNoContent());

        verify(userService).changeOwnPassword(any(), eq(USER_ID));
    }

    @Test
    void changingPasswordRequiresLogin() throws Exception {
        mockMvc.perform(put("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_CHANGE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shortNewPasswordReturns400() throws Exception {
        mockMvc.perform(put("/users/me/password").with(as(UserRole.TREASURER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "current-password", "newPassword": "short"}"""))
                .andExpect(status().isBadRequest());
    }
}
