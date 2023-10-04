/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.featureFlag;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cf.pipeline.FeatureFlagStagePlanCreator;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.timeout.Timeout;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FeatureFlagStagePlanCreatorTest extends CategoryTest {
  FeatureFlagStagePlanCreator featureFlagStagePlanCreator = spy(FeatureFlagStagePlanCreator.class);

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNodes() {
    StageElementConfig stageElementConfig1 = new StageElementConfig();
    PlanCreationContext ctx = PlanCreationContext.builder().yaml("yamlWithUuid").build();
    stageElementConfig1.setTimeout(ParameterField.createValueField(Timeout.builder().timeoutString("10s").build()));
    ctx.setCurrentField(null);
    PlanNode planNode = featureFlagStagePlanCreator.createPlanForParentNode(
        ctx, stageElementConfig1, Collections.singletonList("executionId"));
    assertThat(planNode).isNotNull();
    assertThat(((AbsoluteSdkTimeoutTrackerParameters) planNode.getTimeoutObtainments().get(0).getParameters())
                   .getTimeout()
                   .getValue())
        .isEqualTo("10s");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNodesForEmptyTimeout() {
    StageElementConfig stageElementConfig1 = new StageElementConfig();
    PlanCreationContext ctx = PlanCreationContext.builder().yaml("yamlWithUuid").build();
    PlanNode planNode = featureFlagStagePlanCreator.createPlanForParentNode(
        ctx, stageElementConfig1, Collections.singletonList("executionId"));
    assertThat(planNode).isNotNull();
    stageElementConfig1.setTimeout(null);
    planNode = featureFlagStagePlanCreator.createPlanForParentNode(
        ctx, stageElementConfig1, Collections.singletonList("executionId"));
    assertThat(planNode).isNotNull();
  }
}
