package com.personal.medisynth.config;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 401 for missing, invalid, expired or revoked (inactive account) credentials. */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        ErrorJson.write(request, response, 401, "Unauthorized", "UNAUTHENTICATED",
                "Authentication is required or the token is invalid or expired");
    }
}
