package com.tarnvik.publicbackend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${app.allowed-emails}")
  private String allowedEmailsString;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .cors(cors -> cors.configurationSource(corsConfigurationSource()))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/ping").permitAll()
        .requestMatchers("/api/public/**").permitAll()
        .requestMatchers("/api/auth/me").permitAll()
        .requestMatchers("/api/protected/**").authenticated()
        .anyRequest().authenticated()
      )
      .oauth2Login(oauth2 -> oauth2
        .successHandler(authenticationSuccessHandler())
      )
      .logout(logout -> logout
        .logoutUrl("/api/auth/logout")
        .logoutSuccessUrl("/ping")
        .invalidateHttpSession(true)
        .deleteCookies("JSESSIONID")
      )
      .csrf(csrf -> csrf.disable());
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(frontendUrl));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public AuthenticationSuccessHandler authenticationSuccessHandler() {
    return new AuthenticationSuccessHandler() {
      @Override
      public void onAuthenticationSuccess(HttpServletRequest request,
                                          HttpServletResponse response,
                                          Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute("email");

        List<String> allowedEmails = Arrays.stream(allowedEmailsString.split(","))
          .map(String::trim)
          .toList();

        if (allowedEmails.contains(email)) {
          response.sendRedirect("/ping");
        } else {
          request.getSession().invalidate();
          response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Access denied - you are not authorised to use this application");
        }
      }
    };
  }
}