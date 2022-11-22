/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.METHOD_NAME;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;

import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.data.structure.NullSafeImmutableMap.NullSafeBuilder;
import io.harness.logging.AutoLogContext;

import java.util.Map;

public class NgAutoLogContextForMethod extends AutoLogContext {
  private static Map<String, String> getContext(
      String projectIdentifier, String orgIdentifier, String accountIdentifier, String methodName) {
    NullSafeBuilder<String, String> nullSafeBuilder = NullSafeImmutableMap.builder();
    nullSafeBuilder.putIfNotNull(PROJECT_KEY, projectIdentifier);
    nullSafeBuilder.putIfNotNull(ORG_KEY, orgIdentifier);
    nullSafeBuilder.putIfNotNull(ACCOUNT_KEY, accountIdentifier);
    nullSafeBuilder.putIfNotNull(METHOD_NAME, methodName);
    return nullSafeBuilder.build();
  }

  public NgAutoLogContextForMethod(String projectIdentifier, String orgIdentifier, String accountIdentifier,
      String methodName, OverrideBehavior behavior) {
    super(getContext(projectIdentifier, orgIdentifier, accountIdentifier, methodName), behavior);
  }
}
