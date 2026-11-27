package com.personal.medisynth.config;

import java.io.IOException;
import java.util.Map;

import org.springframework.web.filter.OncePerRequestFilter;

import com.personal.medisynth.service.RateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Throttles the brute-forceable public endpoints per client address. Returns 429 + Retry-After. */
public class RateLimitFilter extends OncePerRequestFilter {

    /** max requests per window. */
    public record Rule(int max, long windowSeconds) {
    }

    private final RateLimiter limiter;
    private final boolean enabled;
    private final Map<String, Rule> rules;

    public RateLimitFilter(RateLimiter limiter, boolean enabled, Map<String, Rule> rules) {
        this.limiter = limiter;
        this.enabled = enabled;
        this.rules = rules;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ruleName = enabled ? ruleFor(request) : null;
        if (ruleName != null) {
            Rule rule = rules.get(ruleName);
            String key = ruleName + ":" + request.getRemoteAddr();
            if (rule != null && !limiter.tryAcquire(key, rule.max(), rule.windowSeconds() * 1000L)) {
                response.setHeader("Retry-After", String.valueOf(rule.windowSeconds()));
                ErrorJson.write(request, response, 429, "Too Many Requests", "RATE_LIMITED",
                        "Too many requests. Please try again later.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String ruleFor(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method)) {
            switch (path) {
                case "/api/auth/login":
                    return "login";
                case "/api/auth/register":
                    return "register";
                case "/api/auth/admin/bootstrap":
                    return "bootstrap";
                default:
                    return null;
            }
        }
        if ("GET".equals(method) && "/api/auth/registration-status".equals(path)) {
            return "status";
        }
        return null;
    }
}
