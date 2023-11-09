/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.beans.IdentifierRef;
import io.harness.utils.IdentifierRefHelper;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ScopedSecretIdToIdentifierRefMapper {
  public IdentifierRef map(
      @NotNull String scopedId, @NotNull String accountId, @Nullable String orgId, @Nullable String projectId) {
    return IdentifierRefHelper.getIdentifierRef(scopedId, accountId, orgId, projectId);
  }
}
