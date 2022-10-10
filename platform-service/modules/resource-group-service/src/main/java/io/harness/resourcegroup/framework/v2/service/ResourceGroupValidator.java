/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;

@OwnedBy(PL)
public interface ResourceGroupValidator {
  boolean sanitizeResourceSelectors(ResourceGroup resourceGroup);
  void validateResourceGroup(ResourceGroupRequest resourceGroupRequest);
}
