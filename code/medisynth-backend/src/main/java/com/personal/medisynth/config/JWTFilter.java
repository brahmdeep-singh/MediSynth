package com.personal.medisynth.config;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.personal.medisynth.model.UserPrincipal;
import com.personal.medisynth.service.JwtService;
import com.personal.medisynth.service.MyUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates a request from "Authorization: Bearer &lt;token&gt;".
 * Only the verified subject (user id) of the token is used. The role and account status are always
 * reloaded from PostgreSQL, so token claims are never trusted and a suspended/rejected user loses
 * access immediately even with an unexpired token.
 * Not a Spring bean on purpose: it is created in {@link SecurityConfig} so it only runs inside the security chain.
 */
public class JWTFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MyUserDetailsService userDetailsService;

    public JWTFilter(JwtService jwtService, MyUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            Long userId = jwtService.extractUserId(token).orElse(null);
            if (userId != null) {
                try {
                    UserPrincipal principal = userDetailsService.loadUserById(userId);
                    if (principal.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (UsernameNotFoundException unknownUser) {
                    // leave unauthenticated -> 401 from the entry point
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
