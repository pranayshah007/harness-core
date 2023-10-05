/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.cache;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeCacheValue;

import java.util.function.Function;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PL)
public interface ScopeCache {
  ScopeCacheValue get(@NotNull Scope key, @NotNull Function<String, ScopeCacheValue> mappingFunction);
  void put(@NotNull Scope key, @NotEmpty ScopeCacheValue value);
  void remove(@NotNull Scope key);
}
