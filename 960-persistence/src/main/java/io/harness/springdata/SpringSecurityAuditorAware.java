package io.harness.springdata;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class SpringSecurityAuditorAware implements AuditorAware<EmbeddedUser> {
  @Override
  public Optional<EmbeddedUser> getCurrentAuditor() {
    try {
      if (SecurityContextBuilder.getPrincipal() == null
          || !PrincipalType.USER.equals(SecurityContextBuilder.getPrincipal().getType())) {
        return Optional.empty();
      }
      UserPrincipal principal = (UserPrincipal) SecurityContextBuilder.getPrincipal();
      EmbeddedUser embeddedUser = EmbeddedUser.builder()
                                      .email(principal.getEmail())
                                      .name(principal.getUsername())
                                      .uuid(principal.getName())
                                      .build();
      return Optional.of(embeddedUser);
    } catch (Exception e) {
      log.error("Something went wrong while trying to read current auditor.", e);
      return Optional.empty();
    }
  }
}
