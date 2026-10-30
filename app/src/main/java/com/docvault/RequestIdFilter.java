package com.docvault;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes one log line per request, tagged with the id NGINX assigned (X-Request-ID),
 * so a single request can be followed through NGINX and Tomcat by searching for its id.
 */
@WebFilter("/*")
public class RequestIdFilter implements Filter {

    /** Only accept ids that look like ones NGINX makes, so a client cannot inject text into our logs. */
    private static final Pattern SAFE = Pattern.compile("[0-9a-f]{16,64}");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.startsWith("/static/")) {
            chain.doFilter(request, response);
            return;
        }

        String rid = req.getHeader("X-Request-ID");
        if (rid == null || !SAFE.matcher(rid).matches()) {
            rid = UUID.randomUUID().toString().replace("-", ""); // request that did not come through NGINX
        }
        if (!resp.containsHeader("X-Request-ID")) {
            resp.setHeader("X-Request-ID", rid);
        }

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            String user = "-";
            try {
                Auth.User u = Auth.current(req);
                if (u != null) {
                    user = u.username();
                }
            } catch (IllegalStateException ignored) {
                // the session was closed during this request (sign-out)
            }
            Log.log("DocVault rid=" + rid + " user=" + user + " " + req.getMethod() + " "
                    + path + " status=" + resp.getStatus() + " tomcat_ms=" + ms);
        }
    }
}
