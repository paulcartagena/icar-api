package com.paulcartagena.icarapi.config;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Finds the client IP used as the rate limit key.
 * <p>
 * By default it is {@link HttpServletRequest#getRemoteAddr()}, which Tomcat resolves from X-Forwarded-For only for
 * private-network proxies (Caddy). The public website calls the API from its own server, so every visitor would
 * share that server's IP: the website sends the visitor's IP in {@value #CLIENT_IP_HEADER} together with the shared
 * secret in {@value #SECRET_HEADER}, and only then is that IP used. Without the right secret both headers are
 * ignored, so a client can't fake its IP to skip the limit.
 */
class ClientIpResolver {

    static final String SECRET_HEADER = "X-Proxy-Secret";
    static final String CLIENT_IP_HEADER = "X-Client-IP";

    // IPv4 or IPv6 characters only (no DNS lookups); anything else falls back to the remote address
    private static final Pattern IP = Pattern.compile("[0-9A-Fa-f.:]{2,45}");

    private final byte[] secret;

    ClientIpResolver(String secret) {
        this.secret = secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    String resolve(HttpServletRequest request) {
        if (secret != null) {
            String sentSecret = request.getHeader(SECRET_HEADER);
            String clientIp = request.getHeader(CLIENT_IP_HEADER);
            if (sentSecret != null && clientIp != null
                    && MessageDigest.isEqual(secret, sentSecret.getBytes(StandardCharsets.UTF_8))
                    && IP.matcher(clientIp.strip()).matches()) {
                return clientIp.strip();
            }
        }
        return request.getRemoteAddr();
    }
}
