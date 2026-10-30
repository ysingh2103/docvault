package com.docvault;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Runs before every request. Anyone not signed in is sent to the login page. */
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI().substring(req.getContextPath().length());

        boolean open = path.equals("/login") || path.startsWith("/static/");
        if (open || Auth.current(req) != null) {
            if (!open) {
                // Pages for signed-in users must not be kept, so Back after sign-out shows nothing.
                resp.setHeader("Cache-Control", "no-store");
            }
            chain.doFilter(request, response);
            return;
        }
        resp.sendRedirect(req.getContextPath() + "/login");
    }
}
