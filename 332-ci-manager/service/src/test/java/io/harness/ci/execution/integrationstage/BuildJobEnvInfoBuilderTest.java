/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class BuildJobEnvInfoBuilderTest extends CIExecutionTestBase {
  public static final String ACCOUNT_ID = "accountId";
  @InjectMocks BuildJobEnvInfoBuilder buildJobEnvInfoBuilder;
  @Mock CIFeatureFlagService ciFeatureFlagService;

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getVmTimeout() {
    int response =
        buildJobEnvInfoBuilder.getTimeout(VmInfraYaml.builder().type(Infrastructure.Type.VM).build(), ACCOUNT_ID);
    assertThat(response).isEqualTo(900 * 1000L);
  }
}
