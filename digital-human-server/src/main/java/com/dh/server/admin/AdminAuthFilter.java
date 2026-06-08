package com.dh.server.admin;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public class AdminAuthFilter implements Filter {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "dhAdmin2024";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String authHeader = httpReq.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String base64Credentials = authHeader.substring(6);
            String credentials = new String(
                Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8
            );
            String[] parts = credentials.split(":", 2);
            if (parts.length == 2 && ADMIN_USERNAME.equals(parts[0])
                && ADMIN_PASSWORD.equals(parts[1])) {
                chain.doFilter(request, response);
                return;
            }
        }

        httpResp.setHeader("WWW-Authenticate", "Basic realm=\"DH Admin\"");
        httpResp.setStatus(401);
    }
}
