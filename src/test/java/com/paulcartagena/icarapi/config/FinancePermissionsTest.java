package com.paulcartagena.icarapi.config;

import com.paulcartagena.icarapi.category.controller.CategoryController;
import com.paulcartagena.icarapi.category.service.CategoryService;
import com.paulcartagena.icarapi.expense.controller.ExpenseController;
import com.paulcartagena.icarapi.expense.service.ExpenseService;
import com.paulcartagena.icarapi.income.controller.IncomeController;
import com.paulcartagena.icarapi.income.service.IncomeService;
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

// Checks every finance endpoint against the permission it requires, using each role's real permission set
@WebMvcTest(
        controllers = {CategoryController.class, ExpenseController.class, IncomeController.class},
        properties = "jwt.secret=test-secret-with-at-least-32-characters!!"
)
@Import(SecurityConfig.class)
class FinancePermissionsTest {

    private static final String ID = UUID.randomUUID().toString();
    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String TODAY = LocalDate.now().toString();
    private static final String RANGE = "?from=" + TODAY + "&to=" + TODAY;

    // Valid bodies: @Valid runs before @PreAuthorize, so an invalid body would return 400 instead of 403
    private static final String CATEGORY = """
            {"name": "Rent", "type": "EXPENSE"}""";
    private static final String CATEGORY_UPDATE = """
            {"name": "Rent"}""";
    private static final String RECORD = """
            {"categoryId": "%s", "amount": 10.00, "date": "%s"}""".formatted(ID, TODAY);
    private static final String VOID = """
            {"reason": "Duplicated"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private ExpenseService expenseService;

    @MockitoBean
    private IncomeService incomeService;

    static Stream<Arguments> endpoints() {
        return Stream.of(
                endpoint(HttpMethod.GET, "/categories", null, Permission.FINANCE_READ),
                endpoint(HttpMethod.POST, "/categories", CATEGORY, Permission.FINANCE_WRITE),
                endpoint(HttpMethod.PUT, "/categories/" + ID, CATEGORY_UPDATE, Permission.FINANCE_WRITE),
                endpoint(HttpMethod.PATCH, "/categories/" + ID + "/activate", null, Permission.FINANCE_WRITE),
                endpoint(HttpMethod.PATCH, "/categories/" + ID + "/deactivate", null, Permission.FINANCE_WRITE),

                endpoint(HttpMethod.GET, "/expenses", null, Permission.FINANCE_READ),
                endpoint(HttpMethod.GET, "/expenses/" + ID, null, Permission.FINANCE_READ),
                endpoint(HttpMethod.GET, "/expenses/summary" + RANGE, null, Permission.FINANCE_READ),
                endpoint(HttpMethod.POST, "/expenses", RECORD, Permission.FINANCE_WRITE),
                endpoint(HttpMethod.PUT, "/expenses/" + ID, RECORD, Permission.FINANCE_WRITE),
                endpoint(HttpMethod.PATCH, "/expenses/" + ID + "/void", VOID, Permission.FINANCE_WRITE),

                endpoint(HttpMethod.GET, "/incomes", null, Permission.FINANCE_READ),
                endpoint(HttpMethod.GET, "/incomes/" + ID, null, Permission.FINANCE_READ),
                endpoint(HttpMethod.GET, "/incomes/summary" + RANGE, null, Permission.FINANCE_READ),
                endpoint(HttpMethod.POST, "/incomes", RECORD, Permission.FINANCE_WRITE),
                endpoint(HttpMethod.PUT, "/incomes/" + ID, RECORD, Permission.FINANCE_WRITE),
                endpoint(HttpMethod.PATCH, "/incomes/" + ID + "/void", VOID, Permission.FINANCE_WRITE)
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

    // "sub" must be a UUID: create endpoints use it as registeredBy
    private static JwtRequestPostProcessor token(Permission... permissions) {
        SimpleGrantedAuthority[] authorities = Stream.of(permissions)
                .map(p -> new SimpleGrantedAuthority(p.name()))
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(jwt -> jwt.subject(USER_ID)).authorities(authorities);
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
}
