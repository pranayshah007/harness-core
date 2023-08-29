/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.ci.stdvars.BuildStandardVariables;

import com.google.inject.Singleton;
import lombok.Builder;

@Singleton
@Builder
@OwnedBy(HarnessTeam.CI)
public class CIPipelineStandardVariablesUtils {
  public static BuildStandardVariables fetchBuildStandardVariables(CIExecutionArgs ciExecutionArgs) {
    return BuildStandardVariables.builder().build();
  }
}
