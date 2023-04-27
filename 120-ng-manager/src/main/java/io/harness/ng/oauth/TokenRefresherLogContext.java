/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.oauth;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AutoLogContext;
import io.harness.ng.userprofile.commons.SCMType;

import com.google.common.collect.ImmutableMap;

@OwnedBy(HarnessTeam.PIPELINE)
public class TokenRefresherLogContext extends AutoLogContext {
  public TokenRefresherLogContext(
      String accountIdentifier, String userIdentifier, SCMType type, OverrideBehavior behavior) {
    super(ImmutableMap.of("accountIdentifier", accountIdentifier, "userIdentifier", userIdentifier, "type",
              type.toString(), "message", "OAuthTokenRefresher"),
        behavior);
  }
}
