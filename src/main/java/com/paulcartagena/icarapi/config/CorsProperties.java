package com.paulcartagena.icarapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

// Frontends (admin panel, public website) allowed to call the API from the browser,
// e.g. CORS_ALLOWED_ORIGINS=https://admin.icar.org,https://icar.org
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @DefaultValue List<String> allowedOrigins
) {
}
