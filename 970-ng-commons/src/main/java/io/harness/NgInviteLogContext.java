/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;

import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

public class NgInviteLogContext extends AutoLogContext {
  public static final String INVITE_IDENTIFIER = "inviteIdentifier";

  public NgInviteLogContext(String accountIdentifier, String inviteIdentifier, OverrideBehavior behavior) {
    super(ImmutableMap.of(ACCOUNT_KEY, accountIdentifier, INVITE_IDENTIFIER, inviteIdentifier), behavior);
  }
}
