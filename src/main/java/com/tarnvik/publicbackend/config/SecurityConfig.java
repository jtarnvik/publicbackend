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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${app.allowed-emails}")
  private String allowedEmailsString;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
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
          // Email is whitelisted - redirect to frontend
          response.sendRedirect("/ping");
        } else {
          // Email not whitelisted - reject and redirect to access denied
          request.getSession().invalidate();
          response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Access denied - you are not authorised to use this application");
        }
      }
    };
  }
}