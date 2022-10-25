/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.logcontext;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.persistence.LogKeyUtils;

import software.wings.beans.User;

import com.google.common.collect.ImmutableMap;

@OwnedBy(HarnessTeam.PL)
public class UserLogContext extends AutoLogContext {
  public static final String ID = LogKeyUtils.calculateLogKeyForId(User.class);

  public UserLogContext(String accountId, String userId, OverrideBehavior behavior) {
    super(ImmutableMap.of(AccountLogContext.ID, accountId, ID, userId), behavior);
  }
}
