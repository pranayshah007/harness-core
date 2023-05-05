/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;

import com.google.inject.Singleton;

@Singleton
@OwnedBy(HarnessTeam.STO)
public class UsageUtils {
  public static String getExecutionUser(ExecutionPrincipalInfo executionPrincipalInfo) {
    switch (executionPrincipalInfo.getPrincipalType()) {
      case USER:
        return executionPrincipalInfo.getPrincipal();
      default:
        return null;
    }
  }
}
