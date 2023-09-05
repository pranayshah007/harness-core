/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.StepGroupPMSPlanCreator;
import io.harness.plancreator.steps.StepGroupPMSPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.rule.Owner;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@Slf4j
@OwnedBy(CI)
public class StepGroupPMSPlanCreatorV2Test extends io.harness.ContainerTestBase {
  @Mock StepGroupPMSPlanCreator stepGroupPMSPlanCreator;
  @InjectMocks StepGroupPMSPlanCreatorV2 stepGroupPMSPlanCreatorV2;

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPopulateParentInfo() {
    PlanCreationContext context = PlanCreationContext.builder().build();
    Map<String, PlanCreationResponse> childrenResponses = Map.of();
    stepGroupPMSPlanCreatorV2.populateParentInfo(context, childrenResponses);
    verify(stepGroupPMSPlanCreator, times(1)).populateParentInfo(context, childrenResponses);
  }
}
