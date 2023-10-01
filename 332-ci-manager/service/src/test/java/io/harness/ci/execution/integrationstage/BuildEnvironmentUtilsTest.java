/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.SHUBHAM;

import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BuildEnvironmentUtilsTest extends CIExecutionTestBase {
  @Inject CIExecutionPlanTestHelper ciExecutionPlanTestHelper;

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getPRBuildEnvironmentVariables() {
    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getPRCIExecutionArgs();
    //    Map<String, String> actual = BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs);
    //    Map<String, String> expected = ciExecutionPlanTestHelper.getPRCIExecutionArgsEnvVars();
    //    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getBranchBuildEnvironmentVariables() {
    //    CIExecutionArgs ciExecutionArgs = ciExecutionPlanTestHelper.getBranchCIExecutionArgs();
    //    Map<String, String> actual = BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs);
    //    Map<String, String> expected = ciExecutionPlanTestHelper.getBranchCIExecutionArgsEnvVars();
    //    assertThat(actual).isEqualTo(expected);
  }
}
