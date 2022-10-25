/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.logger;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;

import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.data.structure.NullSafeImmutableMap.NullSafeBuilder;
import io.harness.logging.logcontext.AutoLogContext;

import java.util.Map;

public class GitProcessingLogContext extends AutoLogContext {
  public static String COMMIT_ID = "commitId";

  private static Map<String, String> getContext(String accountId, String commitId) {
    NullSafeBuilder<String, String> nullSafeBuilder = NullSafeImmutableMap.builder();
    nullSafeBuilder.putIfNotNull(COMMIT_ID, commitId);
    nullSafeBuilder.putIfNotNull(ACCOUNT_KEY, accountId);
    return nullSafeBuilder.build();
  }

  public GitProcessingLogContext(String accountId, String commitId, OverrideBehavior behavior) {
    super(getContext(accountId, commitId), behavior);
  }
}
