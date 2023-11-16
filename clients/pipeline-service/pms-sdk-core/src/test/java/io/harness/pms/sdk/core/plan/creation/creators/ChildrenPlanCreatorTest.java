/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.creators;

import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class ChildrenPlanCreatorTest extends CategoryTest {
  DummyChildrenPlanCreator testChildrenPlanCreator;

  @Before
  public void initialize() {
    testChildrenPlanCreator = Mockito.spy(new DummyChildrenPlanCreator());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStartNodeId() {
    DummyChildrenPlanCreatorParam dummyChildrenPlanCreatorParam = new DummyChildrenPlanCreatorParam();
    assertThat(testChildrenPlanCreator.getStartingNodeId(dummyChildrenPlanCreatorParam)).isNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo() {
    DummyChildrenPlanCreatorParam dummyChildrenPlanCreatorParam = new DummyChildrenPlanCreatorParam();
    assertThat(
        testChildrenPlanCreator.getLayoutNodeInfo(PlanCreationContext.builder().build(), dummyChildrenPlanCreatorParam))
        .isEqualTo(GraphLayoutResponse.builder().build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCreatePlanForField() {
    DummyChildrenPlanCreatorParam dummyChildrenPlanCreatorParam = new DummyChildrenPlanCreatorParam();
    assertThat(testChildrenPlanCreator.createPlanForField(
                   PlanCreationContext.builder().build(), dummyChildrenPlanCreatorParam))
        .isEqualTo(PlanCreationResponse.builder()
                       .graphLayoutResponse(GraphLayoutResponse.builder().build())
                       .node(null, PlanNode.builder().build())
                       .build());
    Mockito.verify(testChildrenPlanCreator).getStartingNodeId(dummyChildrenPlanCreatorParam);
    Mockito.verify(testChildrenPlanCreator)
        .createPlanForChildrenNodes(PlanCreationContext.builder().build(), dummyChildrenPlanCreatorParam);
    Mockito.verify(testChildrenPlanCreator)
        .createPlanForParentNode(
            PlanCreationContext.builder().build(), dummyChildrenPlanCreatorParam, new ArrayList<>());
    Mockito.verify(testChildrenPlanCreator)
        .getLayoutNodeInfo(PlanCreationContext.builder().build(), dummyChildrenPlanCreatorParam);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldTimeout() {
    PlanNodeBuilder planNodeBuilder = testChildrenPlanCreator.setStageTimeoutObtainment(
        ParameterField.createValueField(Timeout.builder().timeoutString("10m").build()), PlanNode.builder());
    assertThat(planNodeBuilder).isNotNull();
    assertThat(planNodeBuilder.build()
                   .getTimeoutObtainments()
                   .get(0)
                   .getParameters()
                   .prepareTimeoutParameters()
                   .getTimeoutMillis())
        .isEqualTo(600000L);
    planNodeBuilder = testChildrenPlanCreator.setStageTimeoutObtainment(
        ParameterField.createExpressionField(true, "<+stage.timeout>", null, true), PlanNode.builder());
    assertThat(planNodeBuilder.build().getTimeoutObtainments().get(0).getParameters().equals(
                   AbsoluteSdkTimeoutTrackerParameters.builder()
                       .timeout(TimeoutUtils.getParameterTimeoutString(
                           ParameterField.createExpressionField(true, "<+stage.timeout>", null, true)))
                       .build()))
        .isTrue();
  }
}
