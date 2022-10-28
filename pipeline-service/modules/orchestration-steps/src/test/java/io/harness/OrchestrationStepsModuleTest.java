/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cf.CFApi;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationStepsModuleTest extends OrchestrationStepsTestBase {
  OrchestrationStepsModule orchestrationStepsModule;
  OrchestrationStepConfig config =
      OrchestrationStepConfig.builder().ffServerBaseUrl("baseUrl").ffServerSSLVerify(true).build();
  @Before
  public void setUp() {
    orchestrationStepsModule = new OrchestrationStepsModule(config);
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetInstance() {
    assertThat(OrchestrationStepsModule.getInstance(OrchestrationStepConfig.builder().build()))
        .isInstanceOf(OrchestrationStepsModule.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProvidesCfAPI() {
    CFApi cfApi = orchestrationStepsModule.providesCfAPI();
    assertEquals(cfApi.getApiClient().getBasePath(), "baseUrl");
    assertEquals(cfApi.getApiClient().isVerifyingSsl(), true);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testOrchestrationStepsConfig() {
    assertEquals(orchestrationStepsModule.orchestrationStepsConfig(), config);
  }
}
