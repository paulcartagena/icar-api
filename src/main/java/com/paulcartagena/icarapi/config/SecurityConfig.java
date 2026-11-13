package com.paulcartagena.icarapi.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class, CorsProperties.class,
        VisitRequestProperties.class, PayPalProperties.class, TrustedProxyProperties.class})
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    private static final RequestMatcher LOGIN = PATHS.matcher(HttpMethod.POST, "/auth/login");
    private static final RequestMatcher NEW_VISIT_REQUEST = PATHS.matcher(HttpMethod.POST, "/public/visit-requests");
    private static final RequestMatcher NEW_PAYPAL_ORDER = PATHS.matcher(HttpMethod.POST, "/public/donations/paypal/orders");

    // Reachable without a token
    private static final RequestMatcher PUBLIC_ROUTES = new OrRequestMatcher(
            // logout is public too: the access token may already be expired when the user logs out
            LOGIN,
            PATHS.matcher(HttpMethod.POST, "/auth/refresh"),
            PATHS.matcher(HttpMethod.POST, "/auth/logout"),
            PATHS.matcher(HttpMethod.GET, "/public/**"),
            // The "Planea tu visita" form of the public website (rate limited below)
            NEW_VISIT_REQUEST,
            // PayPal checkout (create and capture orders) and PayPal's webhook, which verifies its own signature
            PATHS.matcher(HttpMethod.POST, "/public/donations/paypal/**"),
            PATHS.matcher("/scalar/**"),
            PATHS.matcher("/v3/api-docs/**"),
            PATHS.matcher(HttpMethod.GET, "/actuator/health"),
            // Spring forwards errors (e.g. 404) to /error; without this they would turn into 401
            PATHS.matcher("/error"));

    private final HandlerExceptionResolver exceptionResolver;

    // Security errors happen in filters, before any controller runs. Handing them to Spring MVC's resolver
    // lets GlobalExceptionHandler turn them into ErrorResponse like every other error.
    public SecurityConfig(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties authProperties,
                                                   VisitRequestProperties visitRequestProperties,
                                                   PayPalProperties payPalProperties,
                                                   TrustedProxyProperties trustedProxyProperties) throws Exception {
        ClientIpResolver clientIpResolver = new ClientIpResolver(trustedProxyProperties.secret());
        AuthenticationEntryPoint entryPoint = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, ex);
        AccessDeniedHandler accessDeniedHandler = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, ex);

        http
                // Stateless API: the access token travels in the Authorization header, so there is no session
                // cookie to protect against CSRF. The refresh cookie is covered by SameSite=Strict.
                .csrf(csrf -> csrf.disable())
                // Uses the corsConfigurationSource bean below; answers preflight requests before authentication
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Not beans on purpose: Spring Boot would also register them as servlet filters outside this chain
                .addFilterBefore(new RateLimitFilter(LOGIN, clientIpResolver,
                                authProperties.loginRateLimit().capacity(),
                                authProperties.loginRateLimit().refillPeriod(),
                                "Too many login attempts. Try again later.", exceptionResolver),
                        BearerTokenAuthenticationFilter.class)
                .addFilterBefore(new RateLimitFilter(NEW_VISIT_REQUEST, clientIpResolver,
                                visitRequestProperties.rateLimit().capacity(),
                                visitRequestProperties.rateLimit().refillPeriod(),
                                "Too many visit requests. Try again later.", exceptionResolver),
                        BearerTokenAuthenticationFilter.class)
                .addFilterBefore(new RateLimitFilter(NEW_PAYPAL_ORDER, clientIpResolver,
                                payPalProperties.orderRateLimit().capacity(),
                                payPalProperties.orderRateLimit().refillPeriod(),
                                "Too many donation attempts. Try again later.", exceptionResolver),
                        BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    // The admin panel sends the refresh cookie to /auth, so credentials must be allowed;
    // that's also why origins are listed explicitly (the browser rejects "*" with credentials)
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Lets the frontend read when to retry after a 429 from a rate limit (login, visit requests, PayPal orders)
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    // Public routes ignore the Authorization header. Otherwise an expired or invalid token sent along (e.g. by the
    // panel when refreshing, or by Scalar on login) would be rejected with 401 before reaching the endpoint.
    private BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> PUBLIC_ROUTES.matches(request) ? null : defaultResolver.resolve(request);
    }

    // Authorities come from the token's "permissions" claim as-is (e.g. FINANCE_WRITE), so controllers use
    // hasAuthority('FINANCE_WRITE'). The default would read "scope" and add a "SCOPE_" prefix.
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("permissions");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
