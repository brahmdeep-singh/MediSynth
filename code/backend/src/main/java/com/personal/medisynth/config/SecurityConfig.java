package com.personal.medisynth.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.personal.medisynth.service.JwtService;
import com.personal.medisynth.service.MyUserDetailsService;
import com.personal.medisynth.service.RateLimiter;

/**
 * Stateless JWT security. URL rules here are the first line of defence; services re-check ownership
 * and role (IDOR protection) because frontend route guards and URL rules alone are not enough.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final MyUserDetailsService userDetailsService;
    private final RateLimiter rateLimiter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.security.bcrypt-strength:12}")
    private int bcryptStrength;

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.rate-limit.login.max:10}")
    private int loginMax;
    @Value("${app.rate-limit.login.window-seconds:60}")
    private long loginWindow;
    @Value("${app.rate-limit.register.max:10}")
    private int registerMax;
    @Value("${app.rate-limit.register.window-seconds:3600}")
    private long registerWindow;
    @Value("${app.rate-limit.status.max:30}")
    private int statusMax;
    @Value("${app.rate-limit.status.window-seconds:60}")
    private long statusWindow;
    @Value("${app.rate-limit.bootstrap.max:5}")
    private int bootstrapMax;
    @Value("${app.rate-limit.bootstrap.window-seconds:900}")
    private long bootstrapWindow;

    public SecurityConfig(JwtService jwtService, MyUserDetailsService userDetailsService, RateLimiter rateLimiter) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.rateLimiter = rateLimiter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JWTFilter jwtFilter = new JWTFilter(jwtService, userDetailsService);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiter, rateLimitEnabled, Map.of(
                "login", new RateLimitFilter.Rule(loginMax, loginWindow),
                "register", new RateLimitFilter.Rule(registerMax, registerWindow),
                "status", new RateLimitFilter.Rule(statusMax, statusWindow),
                "bootstrap", new RateLimitFilter.Rule(bootstrapMax, bootstrapWindow)));

        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable()) // stateless bearer-token API: no cookies, no session
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint())
                        .accessDeniedHandler(new RestAccessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/admin/bootstrap").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/registration-status", "/api/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/patients/**").hasRole("PATIENT")
                        .requestMatchers("/api/doctors/me", "/api/doctors/me/**").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/appointments").hasRole("PATIENT")
                        .requestMatchers("/api/appointments/my").hasAnyRole("PATIENT", "DOCTOR")
                        .requestMatchers("/api/lab/**").hasRole("LAB_TECHNICIAN")
                        .requestMatchers(HttpMethod.POST, "/api/lab-tests").hasRole("DOCTOR")
                        .requestMatchers("/api/lab-tests/my").hasAnyRole("PATIENT", "DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/lab-tests/*/cancel").hasRole("DOCTOR")
                        .requestMatchers("/api/research/**").hasRole("RESEARCHER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JWTFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins must list explicit origins (no wildcards), e.g. http://localhost:5173");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Bootstrap-Secret"));
        cors.setExposedHeaders(List.of("Content-Disposition")); // lets the frontend read the CSV filename
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
