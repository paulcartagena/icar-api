package com.paulcartagena.icarapi.config;

import com.paulcartagena.icarapi.event.controller.EventController;
import com.paulcartagena.icarapi.event.controller.PublicEventController;
import com.paulcartagena.icarapi.event.service.EventService;
import com.paulcartagena.icarapi.sitecontent.controller.PublicSiteContentController;
import com.paulcartagena.icarapi.sitecontent.controller.SiteContentController;
import com.paulcartagena.icarapi.sitecontent.service.SiteContentService;
import com.paulcartagena.icarapi.user.enums.Permission;
import com.paulcartagena.icarapi.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Checks every content endpoint (events, site content) against the permission it requires, and that the public
// ones need no token
@WebMvcTest(
        controllers = {EventController.class, PublicEventController.class,
                SiteContentController.class, PublicSiteContentController.class},
        properties = "jwt.secret=test-secret-with-at-least-32-characters!!"
)
@Import(SecurityConfig.class)
class ContentPermissionsTest {

    private static final String ID = UUID.randomUUID().toString();
    private static final String OTHER_ID = UUID.randomUUID().toString();

    // Valid bodies: @Valid runs before @PreAuthorize, so an invalid body would return 400 instead of 403
    private static final String EVENT = """
            {"title": "Culto", "description": "Culto dominical",
             "startAt": "2026-12-24T19:00:00", "endAt": "2026-12-24T21:00:00"}""";
    private static final String PHOTO = """
            {"imageUrl": "https://cdn.icar.org/1.jpg"}""";
    private static final String ABOUT_US = """
            {"title": "Quiénes somos", "body": "Somos una iglesia", "imageUrl": "https://cdn.icar.org/a.jpg"}""";
    private static final String CONTACT_INFO = """
            {"address": "Calle 1", "phone": "555-1234", "email": "info@icar.org",
             "mapUrl": "https://maps.example.com/icar"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private SiteContentService siteContentService;

    static Stream<Arguments> endpoints() {
        return Stream.of(
                endpoint(HttpMethod.GET, "/events?active=true", null),
                endpoint(HttpMethod.GET, "/events/" + ID, null),
                endpoint(HttpMethod.POST, "/events", EVENT),
                endpoint(HttpMethod.PUT, "/events/" + ID, EVENT),
                endpoint(HttpMethod.PATCH, "/events/" + ID + "/activate", null),
                endpoint(HttpMethod.PATCH, "/events/" + ID + "/deactivate", null),
                endpoint(HttpMethod.DELETE, "/events/" + ID, null),
                endpoint(HttpMethod.POST, "/events/" + ID + "/photos", PHOTO),
                endpoint(HttpMethod.PUT, "/events/" + ID + "/photos/" + OTHER_ID, PHOTO),
                endpoint(HttpMethod.DELETE, "/events/" + ID + "/photos/" + OTHER_ID, null),
                endpoint(HttpMethod.PUT, "/site-content/about-us", ABOUT_US),
                endpoint(HttpMethod.PUT, "/site-content/contact-info", CONTACT_INFO)
        );
    }

    // Every admin content endpoint requires CONTENT_WRITE
    private static Arguments endpoint(HttpMethod method, String url, String body) {
        return Arguments.of(method, url, body);
    }

    private MockHttpServletRequestBuilder call(HttpMethod method, String url, String body) {
        MockHttpServletRequestBuilder builder = request(method, url);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return builder;
    }

    private static JwtRequestPostProcessor token(Permission... permissions) {
        SimpleGrantedAuthority[] authorities = Stream.of(permissions)
                .map(p -> new SimpleGrantedAuthority(p.name()))
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString())).authorities(authorities);
    }

    @ParameterizedTest(name = "{0} {1} allows every role with CONTENT_WRITE")
    @MethodSource("endpoints")
    void rolesWithThePermissionAreAllowed(HttpMethod method, String url, String body) throws Exception {
        for (UserRole role : UserRole.values()) {
            if (!role.getPermissions().contains(Permission.CONTENT_WRITE)) {
                continue;
            }
            mockMvc.perform(call(method, url, body)
                            .with(token(role.getPermissions().toArray(Permission[]::new))))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @ParameterizedTest(name = "{0} {1} rejects tokens without CONTENT_WRITE")
    @MethodSource("endpoints")
    void tokensWithoutThePermissionGet403(HttpMethod method, String url, String body) throws Exception {
        Permission[] others = Stream.of(Permission.values())
                .filter(p -> p != Permission.CONTENT_WRITE)
                .toArray(Permission[]::new);

        mockMvc.perform(call(method, url, body).with(token(others)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest(name = "{0} {1} rejects TREASURER")
    @MethodSource("endpoints")
    void treasurerCantManageContent(HttpMethod method, String url, String body) throws Exception {
        mockMvc.perform(call(method, url, body)
                        .with(token(UserRole.TREASURER.getPermissions().toArray(Permission[]::new))))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicEventsNeedNoToken() throws Exception {
        mockMvc.perform(get("/public/events")).andExpect(status().isOk());
        mockMvc.perform(get("/public/events?from=2026-12-01&to=2026-12-31")).andExpect(status().isOk());
        mockMvc.perform(get("/public/events/" + ID)).andExpect(status().isOk());
    }

    @Test
    void publicSiteContentNeedsNoToken() throws Exception {
        mockMvc.perform(get("/public/about-us")).andExpect(status().isOk());
        mockMvc.perform(get("/public/contact-info")).andExpect(status().isOk());
    }

    @Test
    void adminEventsWithoutTokenReturn401() throws Exception {
        mockMvc.perform(get("/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyGetIsPublic() throws Exception {
        // POST isn't mapped under /public, but it must be rejected by security before anything else
        mockMvc.perform(post("/public/events").contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isUnauthorized());
    }
}
