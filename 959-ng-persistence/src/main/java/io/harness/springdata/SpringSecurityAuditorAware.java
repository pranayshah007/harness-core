/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
