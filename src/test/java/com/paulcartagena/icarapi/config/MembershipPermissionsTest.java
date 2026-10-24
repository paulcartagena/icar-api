package com.paulcartagena.icarapi.config;

import com.paulcartagena.icarapi.family.controller.FamilyController;
import com.paulcartagena.icarapi.family.service.FamilyService;
import com.paulcartagena.icarapi.member.controller.MemberController;
import com.paulcartagena.icarapi.member.service.MemberService;
import com.paulcartagena.icarapi.ministry.controller.MinistryController;
import com.paulcartagena.icarapi.ministry.service.MinistryService;
import com.paulcartagena.icarapi.user.enums.Permission;
import com.paulcartagena.icarapi.user.enums.UserRole;
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

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Checks every membership endpoint against the permission it requires, using each role's real permission set
@WebMvcTest(
        controllers = {MemberController.class, FamilyController.class, MinistryController.class},
        properties = "jwt.secret=test-secret-with-at-least-32-characters!!"
)
@Import(SecurityConfig.class)
class MembershipPermissionsTest {

    private static final String ID = UUID.randomUUID().toString();
    private static final String OTHER_ID = UUID.randomUUID().toString();
    private static final String TODAY = LocalDate.now().toString();

    // Valid bodies: @Valid runs before @PreAuthorize, so an invalid body would return 400 instead of 403
    private static final String MEMBER = """
            {"firstName": "Ana", "lastName": "Pérez", "joinDate": "%s", "status": "NEW"}""".formatted(TODAY);
    private static final String FAMILY = """
            {"familyName": "Pérez", "responsibleMemberId": "%s"}""".formatted(ID);
    private static final String MINISTRY = """
            {"name": "Alabanza"}""";
    private static final String MINISTRY_MEMBER = """
            {"memberId": "%s"}""".formatted(ID);
    private static final String SCHEDULE = """
            {"dayOfWeek": "SUNDAY", "startTime": "10:00"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private FamilyService familyService;

    @MockitoBean
    private MinistryService ministryService;

    static Stream<Arguments> endpoints() {
        return Stream.of(
                endpoint(HttpMethod.GET, "/members?status=ACTIVE", null, Permission.MEMBERS_READ),
                endpoint(HttpMethod.GET, "/members/" + ID, null, Permission.MEMBERS_READ),
                endpoint(HttpMethod.POST, "/members", MEMBER, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.PUT, "/members/" + ID, MEMBER, Permission.MEMBERS_WRITE),

                endpoint(HttpMethod.GET, "/families", null, Permission.MEMBERS_READ),
                endpoint(HttpMethod.GET, "/families/" + ID, null, Permission.MEMBERS_READ),
                endpoint(HttpMethod.POST, "/families", FAMILY, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.PUT, "/families/" + ID, FAMILY, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.DELETE, "/families/" + ID, null, Permission.MEMBERS_WRITE),

                endpoint(HttpMethod.GET, "/ministries", null, Permission.MEMBERS_READ),
                endpoint(HttpMethod.GET, "/ministries/" + ID, null, Permission.MEMBERS_READ),
                endpoint(HttpMethod.POST, "/ministries", MINISTRY, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.PUT, "/ministries/" + ID, MINISTRY, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.DELETE, "/ministries/" + ID, null, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.GET, "/ministries/" + ID + "/members", null, Permission.MEMBERS_READ),
                endpoint(HttpMethod.POST, "/ministries/" + ID + "/members", MINISTRY_MEMBER, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.DELETE, "/ministries/" + ID + "/members/" + OTHER_ID, null, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.POST, "/ministries/" + ID + "/schedules", SCHEDULE, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.PUT, "/ministries/" + ID + "/schedules/" + OTHER_ID, SCHEDULE, Permission.MEMBERS_WRITE),
                endpoint(HttpMethod.DELETE, "/ministries/" + ID + "/schedules/" + OTHER_ID, null, Permission.MEMBERS_WRITE)
        );
    }

    private static Arguments endpoint(HttpMethod method, String url, String body, Permission required) {
        return Arguments.of(method, url, body, required);
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

    @ParameterizedTest(name = "{0} {1} allows every role with {3}")
    @MethodSource("endpoints")
    void rolesWithThePermissionAreAllowed(HttpMethod method, String url, String body, Permission required) throws Exception {
        for (UserRole role : UserRole.values()) {
            if (!role.getPermissions().contains(required)) {
                continue;
            }
            mockMvc.perform(call(method, url, body)
                            .with(token(role.getPermissions().toArray(Permission[]::new))))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @ParameterizedTest(name = "{0} {1} rejects tokens without {3}")
    @MethodSource("endpoints")
    void tokensWithoutThePermissionGet403(HttpMethod method, String url, String body, Permission required) throws Exception {
        // Every permission except the required one
        Permission[] others = Stream.of(Permission.values()).filter(p -> p != required).toArray(Permission[]::new);

        mockMvc.perform(call(method, url, body).with(token(others)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest(name = "{0} {1} rejects TREASURER")
    @MethodSource("endpoints")
    void treasurerCantAccessMembership(HttpMethod method, String url, String body, Permission required) throws Exception {
        mockMvc.perform(call(method, url, body)
                        .with(token(UserRole.TREASURER.getPermissions().toArray(Permission[]::new))))
                .andExpect(status().isForbidden());
    }
}
