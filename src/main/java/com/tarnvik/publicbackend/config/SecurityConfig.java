package com.tarnvik.publicbackend.config;

import com.tarnvik.publicbackend.commuter.service.AllowedUserService;
import com.tarnvik.publicbackend.commuter.service.PendingUserService;
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
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final AllowedUserService allowedUserService;
  private final PendingUserService pendingUserService;
  private final String frontendUrl;

  public SecurityConfig(AllowedUserService allowedUserService,
                        PendingUserService pendingUserService,
                        @Value("${app.frontend-url}") String frontendUrl) {
    this.allowedUserService = allowedUserService;
    this.pendingUserService = pendingUserService;
    this.frontendUrl = frontendUrl;
  }

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

        if (allowedUserService.isEmailAllowed(email)) {
          response.sendRedirect(frontendUrl + "/sl-dashboard/");
        } else {
          String name = oauth2User.getAttribute("name");
          pendingUserService.recordLoginAttempt(email, name);
          request.getSession().invalidate();
          response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Access denied - you are not authorised to use this application");
        }
      }
    };
  }

  @Bean
  public CookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieMaxAge(30 * 24 * 60 * 60); // 30 days in seconds
    return serializer;
  }
}
