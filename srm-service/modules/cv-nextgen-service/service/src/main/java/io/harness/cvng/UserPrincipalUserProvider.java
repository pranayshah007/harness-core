/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng;

import io.harness.beans.EmbeddedUser;
import io.harness.persistence.UserProvider;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;

public class UserPrincipalUserProvider implements UserProvider {
  @Override
  public EmbeddedUser activeUser() {
    Principal principal = SecurityContextBuilder.getPrincipal();
    if (principal != null && principal.getType().equals(PrincipalType.USER)) {
      UserPrincipal userPrincipal = (UserPrincipal) principal;
      return EmbeddedUser.builder()
          .name(userPrincipal.getUsername())
          .email(userPrincipal.getEmail())
          .uuid(userPrincipal.getName())
          .build();
    } else {
      return null;
    }
  }
}
