package com.docvault;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * The address of the real visitor. Tomcat only sees NGINX (127.0.0.1), so NGINX passes the visitor's
 * address in X-Real-IP. That header is believed only when the connection really comes from this
 * machine; a request that reaches Tomcat any other way keeps its own address, so it cannot be faked.
 */
final class ClientIp {

    /** Digits, hex letters, dots and colons only, so the value cannot inject text into the logs. */
    private static final Pattern SAFE = Pattern.compile("[0-9a-fA-F:.]{3,45}");

    private ClientIp() { }

    static String of(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        boolean fromNginx = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (fromNginx) {
            String real = req.getHeader("X-Real-IP");
            if (real != null && SAFE.matcher(real.trim()).matches()) {
                return real.trim();
            }
        }
        return remote;
    }
}
