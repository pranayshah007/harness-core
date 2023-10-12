/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.core.ci.dashboard;

import io.harness.ci.cache.CICacheManagementService;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;

import com.google.inject.Inject;

public class CICacheAllowanceImpl implements RestrictionUsageInterface<StaticLimitRestrictionMetadataDTO> {
  @Inject CICacheManagementService ciCacheManagementService;

  @Override
  public long getCurrentValue(String accountIdentifier, StaticLimitRestrictionMetadataDTO restrictionMetadataDTO) {
    return ciCacheManagementService.getCacheMetadata(accountIdentifier).getUsed();
  }
}
