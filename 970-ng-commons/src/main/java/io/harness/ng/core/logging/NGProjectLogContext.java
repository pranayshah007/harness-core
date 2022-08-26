/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.logging;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

@OwnedBy(HarnessTeam.PL)
public class NGProjectLogContext extends AutoLogContext {
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  public NGProjectLogContext(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, OverrideBehavior behavior) {
    super(ImmutableMap.of(ACCOUNT_IDENTIFIER, accountIdentifier, ORG_IDENTIFIER, orgIdentifier, PROJECT_IDENTIFIER,
              projectIdentifier),
        behavior);
  }
}
