package com.tarnvik.publicbackend.config;

import com.tarnvik.publicbackend.commuter.model.domain.repository.AllowedUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class OidcRoleEnrichmentService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private final AllowedUserRepository allowedUserRepository;
  private final OidcUserService delegate = new OidcUserService();

  public OidcRoleEnrichmentService(AllowedUserRepository allowedUserRepository) {
    this.allowedUserRepository = allowedUserRepository;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = delegate.loadUser(userRequest);
    String email = oidcUser.getEmail();

    Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());

    allowedUserRepository.findByEmail(email)
        .filter(user -> user.getRole() != null)
        .ifPresent(user -> authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole())));

    return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
  }
}
