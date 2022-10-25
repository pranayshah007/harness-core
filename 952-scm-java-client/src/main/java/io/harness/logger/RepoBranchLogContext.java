/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logger;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.data.structure.NullSafeImmutableMap.NullSafeBuilder;
import io.harness.logging.logcontext.AutoLogContext;

import java.util.Map;

@OwnedBy(DX)
public class RepoBranchLogContext extends AutoLogContext {
  private static Map<String, String> getContext(String slug, String branchName, String ref) {
    NullSafeBuilder<String, String> nullSafeBuilder = NullSafeImmutableMap.builder();
    nullSafeBuilder.putIfNotNull("branchName", branchName);
    nullSafeBuilder.putIfNotNull("ref", ref);
    nullSafeBuilder.putIfNotNull("slug", slug);
    return nullSafeBuilder.build();
  }

  public RepoBranchLogContext(String slug, String branchName, String ref, OverrideBehavior behavior) {
    super(getContext(slug, branchName, ref), behavior);
  }
}
