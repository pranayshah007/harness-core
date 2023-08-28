/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage;

import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.stages.IntegrationStageNode;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefData;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NumberNGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IntegrationStagePMSPlanCreatorV2Test {
  IntegrationStagePMSPlanCreatorV2 integrationStagePMSPlanCreatorV2 = new IntegrationStagePMSPlanCreatorV2();

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testAddPipelineVariablesToStageNode() {
    String yaml = "pipeline:\n"
        + "  name: variables\n"
        + "  variables:\n"
        + "    - name: number\n"
        + "      type: Number\n"
        + "      description: \"\"\n"
        + "      value: 23\n"
        + "    - name: string\n"
        + "      type: String\n"
        + "      description: \"\"\n"
        + "      value: someString\n"
        + "    - name: secret\n"
        + "      type: Secret\n"
        + "      description: \"\"\n"
        + "      value: stage_secret_large\n";
    PlanCreationContext planCreationContext = PlanCreationContext.builder().yaml(yaml).build();
    IntegrationStageNode integrationStageNode = IntegrationStageNode.builder().build();
    integrationStagePMSPlanCreatorV2.addPipelineVariablesToStageNode(planCreationContext, integrationStageNode);
    List<NGVariable> pipelineVariables = integrationStageNode.getPipelineVariables();
    assertThat(pipelineVariables).isNotEmpty();
    assertThat(pipelineVariables.get(0)).isInstanceOf(NumberNGVariable.class);
    assertThat(pipelineVariables.get(0).getCurrentValue().getValue()).isEqualTo(23.0);
    assertThat(pipelineVariables.get(1)).isInstanceOf(StringNGVariable.class);
    assertThat(pipelineVariables.get(1).getCurrentValue().getValue()).isEqualTo("someString");
    assertThat(pipelineVariables.get(2)).isInstanceOf(SecretNGVariable.class);
    assertThat(pipelineVariables.get(2).getCurrentValue().getValue()).isInstanceOf(SecretRefData.class);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testAddPipelineVariablesToStageNode_empty() {
    String yaml = "pipeline:\n"
        + "  name: variables\n";
    PlanCreationContext planCreationContext = PlanCreationContext.builder().yaml(yaml).build();
    IntegrationStageNode integrationStageNode = IntegrationStageNode.builder().build();
    integrationStagePMSPlanCreatorV2.addPipelineVariablesToStageNode(planCreationContext, integrationStageNode);
    List<NGVariable> pipelineVariables = integrationStageNode.getPipelineVariables();
    assertThat(pipelineVariables).isEmpty();
  }
}
