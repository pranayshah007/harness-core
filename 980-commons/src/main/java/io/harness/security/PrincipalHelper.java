/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class PrincipalHelper {
  // If an author has no public email listed in their GitLab profile, the email attribute in the webhook payload
  // displays a value of [REDACTED].
  private static String GITLAB_EMAIL_RESPONSE_KEYWORD = "[REDACTED]";
  public String getUuid(Principal principal) {
    return principal == null ? null : principal.getName();
  }

  public String getIdentifier(Principal principal) {
    return principal == null ? null : principal.getName();
  }

  public String getEmail(Principal principal) {
    if (principal == null) {
      return null;
    }
    String email;
    switch (principal.getType()) {
      case USER:
        email = ((UserPrincipal) principal).getEmail();
        break;
      case SERVICE_ACCOUNT:
        email = ((ServiceAccountPrincipal) principal).getEmail();
        break;
      default:
        email = null;
    }

    if (email == null || email.equals(GITLAB_EMAIL_RESPONSE_KEYWORD)) {
      return null;
    }
    return email;
  }

  public String getUsername(Principal principal) {
    if (principal == null) {
      return null;
    }
    switch (principal.getType()) {
      case USER:
        return ((UserPrincipal) principal).getUsername();
      case SERVICE_ACCOUNT:
        return ((ServiceAccountPrincipal) principal).getUsername();
      default:
        return null;
    }
  }
}
