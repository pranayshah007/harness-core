/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.ScopeAware;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class ResourceScopeUtils {
  public ResourceScope getEntityScope(ScopeAware entity) {
    if (isNotEmpty(entity.getProjectIdentifier())) {
      return new ProjectScope(entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier());
    } else if (isNotEmpty(entity.getOrgIdentifier())) {
      return new OrgScope(entity.getAccountId(), entity.getOrgIdentifier());
    }
    return new AccountScope(entity.getAccountId());
  }
}
