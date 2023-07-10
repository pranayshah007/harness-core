/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.customstage;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CustomStageStepTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @InjectMocks CustomStageStep customStageStep;

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateStepParametersClass() {
    assertThat(customStageStep.getStepParametersClass()).isInstanceOf(java.lang.Class.class);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateObtainChild() {
    String id = "tempid";
    StageElementParameters params =
        StageElementParameters.builder().specConfig(CustomStageSpecParams.builder().childNodeID(id).build()).build();
    ChildExecutableResponse childExecutableResponse =
        customStageStep.obtainChild(Ambiance.newBuilder().build(), params, StepInputPackage.builder().build());
    assertThat(childExecutableResponse).isEqualTo(ChildExecutableResponse.newBuilder().setChildNodeId(id).build());
  }
}
