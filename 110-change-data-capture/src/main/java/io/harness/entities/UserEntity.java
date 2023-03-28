/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.NGUserChangeDataHandler;
import io.harness.ng.core.user.entities.UserMetadata;

import com.google.inject.Inject;

public class UserEntity implements CDCEntity<UserMetadata> {
  @Inject private NGUserChangeDataHandler ngUserChangeDataHandler;
  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    return ngUserChangeDataHandler;
  }

  @Override
  public Class<UserMetadata> getSubscriptionEntity() {
    return UserMetadata.class;
  }
}
