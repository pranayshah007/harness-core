/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.plancreator;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;

import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineStagePlanCreatorTest {
  PipelineStagePlanCreator pipelineStagePlanCreator = new PipelineStagePlanCreator();

  private String ORG = "org";
  private String PROJ = "proj";
  private String PIPELINE = "pipeline";
  private String ACC = "acc";
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(pipelineStagePlanCreator.getFieldClass()).isEqualTo(PipelineStageNode.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(pipelineStagePlanCreator.getSupportedTypes().get(YAMLFieldNameConstants.STAGE))
        .contains(StepSpecTypeConstants.PIPELINE_STAGE);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStepParameter() {
    PipelineStageConfig config = PipelineStageConfig.builder()
                                     .pipeline(PIPELINE)
                                     .org(ORG)
                                     .project(PROJ)
                                     .inputSetReferences(Collections.singletonList("ref"))
                                     .build();
    PipelineStageStepParameters stepParameters = pipelineStagePlanCreator.getStepParameter(config);
    assertThat(stepParameters.getPipeline()).isEqualTo(PIPELINE);
    assertThat(stepParameters.getOrg()).isEqualTo(ORG);
    assertThat(stepParameters.getProject()).isEqualTo(PROJ);
    assertThat(stepParameters.getInputSetReferences().size()).isEqualTo(1);
    assertThat(stepParameters.getInputSetReferences().get(0)).isEqualTo("ref");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForField() throws IOException {
    String yamlField = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: parent_pipeline\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(pipelineStageYamlField).build();

    PipelineStageNode pipelineStageNode = YamlUtils.read(yamlField, PipelineStageNode.class);
    PlanCreationResponse response =
        pipelineStagePlanCreator.createPlanForField(ctx, YamlUtils.read(yamlField, PipelineStageNode.class));
    assertThat(response.getPlanNode()).isNotNull();
    PlanNode planNode = response.getPlanNode();
    assertThat(planNode.getName()).isEqualTo(pipelineStageNode.getName());
    assertThat(planNode.getIdentifier()).isEqualTo(pipelineStageNode.getIdentifier());
    assertThat(planNode.getGroup()).isEqualTo(StepCategory.STAGE.name());
    assertThat(planNode.getFacilitatorObtainments().get(0).getType().getType())
        .isEqualTo(OrchestrationFacilitatorType.ASYNC);
  }
}
