/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.persistence.UserProvider;

import software.wings.beans.User;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Slf4j
public class ThreadLocalUserProvider implements UserProvider {
  public static EmbeddedUser populateEmbeddedUser(User user) {
    EmbeddedUser embeddedUser = EmbeddedUser.builder()
                                    .uuid(UserThreadLocal.get().getUuid())
                                    .email(UserThreadLocal.get().getEmail())
                                    .name(UserThreadLocal.get().getName())
                                    .build();
    if (StringUtils.isNotEmpty(UserThreadLocal.get().getExternalUserId())) {
      embeddedUser.setExternalUserId(UserThreadLocal.get().getExternalUserId());
    }
    return embeddedUser;
  }

  public static EmbeddedUser threadLocalUser() {
    User user = UserThreadLocal.get();
    if (user == null) {
      return null;
    }
    return populateEmbeddedUser(UserThreadLocal.get());
  }

  @Override
  public EmbeddedUser activeUser() {
    return threadLocalUser();
  }
}
