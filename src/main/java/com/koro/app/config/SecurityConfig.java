package com.koro.app.config;

import com.koro.app.auth.security.AuthEntryPointJwt;
import com.koro.app.auth.security.AuthTokenFilter;
import com.koro.app.auth.security.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
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
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://localhost:3030",
                "http://localhost:8080",
                "https://koro-website.rosdekoc12.workers.dev",
                "https://korot.site",
                "https://www.korot.site"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/", "/health").permitAll()
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/api/v1/languages").permitAll()
                    .requestMatchers("/api/v1/translations", "/api/v1/translations/**").permitAll()
                    // Exported PDF files are shared by their unguessable (UUID) filename — public so a
                    // "publish / share this book" link works without the recipient signing in.
                    .requestMatchers(HttpMethod.GET, "/api/v1/export/files/**").permitAll()
                    .requestMatchers(
                                "/api/v1/languages",
                                "/api/v1/languages/**",

                                "/api/v1/categories",
                                "/api/v1/categories/**",

                                "/api/v1/concepts",
                                "/api/v1/concepts/**",

                                "/api/v1/translations",
                                "/api/v1/translations/**"
                        ).permitAll()
                    .requestMatchers("/h2-console/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/v1/leaderboard", "/api/v1/leaderboard/**").permitAll()
                    .requestMatchers("/api/v1/admin/submissions/**").hasAnyRole("ADMIN", "LANGUAGE_REVIEWER", "MODERATOR")
                    .requestMatchers("/api/v1/admin/leaderboard/**").hasAnyRole("ADMIN", "MODERATOR")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/admin/concepts/*", "/api/v1/admin/translations/*").hasAnyRole("ADMIN", "MODERATOR")
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/languages").hasAnyRole("ADMIN", "MODERATOR")
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            );
        
        // Disable Frame Options for H2 console
        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));
        
        http.authenticationProvider(authenticationProvider());
        
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
