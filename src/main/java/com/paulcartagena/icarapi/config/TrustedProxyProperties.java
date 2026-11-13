package com.paulcartagena.icarapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Secret shared with the public website's server (TRUSTED_PROXY_SECRET). Requests that carry it may say which
// visitor they are acting for (see ClientIpResolver). Blank means no proxy is trusted.
@ConfigurationProperties(prefix = "trusted-proxy")
public record TrustedProxyProperties(String secret) {
}
