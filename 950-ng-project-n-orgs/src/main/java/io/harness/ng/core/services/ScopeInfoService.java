/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.services;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.beans.ScopeInfo;

import java.util.Optional;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
public interface ScopeInfoService {
  String SCOPE_INFO_DATA_CACHE_KEY = "scopeInfoDataCache";

  Optional<ScopeInfo> getScopeInfo(@NotEmpty String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void addScopeInfoToCache(@NotEmpty String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeLevel scopeType, String uniqueId);

  boolean removeScopeInfoFromCache(@NotEmpty String accountIdentifier, String orgIdentifier, String projectIdentifier);
}
