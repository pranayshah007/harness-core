/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.scopes.core.ScopeHelper.getAccountFromScopeIdentifier;

import static java.util.Objects.isNull;

import io.harness.aggregator.AccessControlAdminService;

public abstract class AbstractAccessControlChangeConsumer<T extends AccessControlChangeEventData>
    implements AccessControlChangeConsumer<T> {
  private final AccessControlAdminService accessControlAdminService;

  protected AbstractAccessControlChangeConsumer(AccessControlAdminService accessControlAdminService) {
    this.accessControlAdminService = accessControlAdminService;
  }

  public boolean shouldBlock(AccessControlChangeEventData changeEventData) {
    if (isNull(changeEventData.getScope()) || changeEventData.getScope().isEmpty()) {
      return false;
    }
    return accessControlAdminService.isBlocked(
        getAccountFromScopeIdentifier(changeEventData.getScope().get().toString()));
  }
}
