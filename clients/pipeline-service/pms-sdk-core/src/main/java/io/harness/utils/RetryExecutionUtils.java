/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.RetryExecutionInfo;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@UtilityClass
public class RetryExecutionUtils {
  public String getRootExecutionId(Ambiance ambiance, RetryExecutionInfo retryExecutionInfo) {
    String rootExecutionId = ambiance.getPlanExecutionId();
    if (isRetry(retryExecutionInfo)) {
      rootExecutionId = retryExecutionInfo.getRootExecutionId();
    }
    return rootExecutionId;
  }

  public String getParentExecutionId(Ambiance ambiance, RetryExecutionInfo retryExecutionInfo) {
    String parentExecutionId = ambiance.getPlanExecutionId();
    if (isRetry(retryExecutionInfo)) {
      parentExecutionId = retryExecutionInfo.getParentRetryId();
    }
    return parentExecutionId;
  }

  private boolean isRetry(RetryExecutionInfo retryExecutionInfo) {
    if (retryExecutionInfo != null && retryExecutionInfo.getIsRetry()) {
      return true;
    }
    return false;
  }
}
