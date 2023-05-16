/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventHandlers;

import io.harness.beans.Scope;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;

import com.google.inject.Inject;

public class ResourceGroupCRUDEventHandler {
  ResourceGroupService resourceGroupService;

  @Inject
  public ResourceGroupCRUDEventHandler(ResourceGroupService resourceGroupService) {
    this.resourceGroupService = resourceGroupService;
  }
  public boolean deleteAssociatedResourceGroup(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();

    resourceGroupService.deleteByScope(scope);
    return true;
  }
}
