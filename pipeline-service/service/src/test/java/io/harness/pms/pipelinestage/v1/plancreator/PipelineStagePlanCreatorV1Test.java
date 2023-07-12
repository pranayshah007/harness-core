/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.v1.plancreator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineServiceImpl;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.helper.PipelineStageHelper;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.utils.PmsFeatureFlagService;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStagePlanCreatorV1Test {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock PipelineStageHelper pipelineStageHelper;
  @Mock KryoSerializer kryoSerializer;
  @Mock PMSPipelineServiceImpl pmsPipelineService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @InjectMocks PipelineStagePlanCreatorV1 pipelineStagePlanCreator;

  private String ORG = "org";
  private String PROJ = "proj";
  private String PIPELINE = "pipeline";
  private String ACC = "acc";

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(pipelineStagePlanCreator.getFieldClass()).isEqualTo(YamlField.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(pipelineStagePlanCreator.getSupportedTypes().get(YAMLFieldNameConstants.STAGE))
        .contains(StepSpecTypeConstants.PIPELINE_STAGE);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetStepParameter() throws IOException {
    String pipelineInputs = "---\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "type: \"Pipeline\"\n"
        + "stages:\n"
        + "   - stage:\n"
        + "       spec:\n"
        + "         pipeline: \"childPipeline\"\n"
        + "         org: \"org\"\n";

    YamlField yamlField = YamlUtils.readTreeWithDefaultObjectMapper(pipelineInputs);
    PipelineStageConfig config = PipelineStageConfig.builder()
                                     .pipeline(PIPELINE)
                                     .org(ORG)
                                     .project(PROJ)
                                     .inputSetReferences(Collections.singletonList("ref"))
                                     .build();
    doReturn(null).when(pipelineStageHelper).getInputSetJsonNode(yamlField, PipelineVersion.V1);

    PipelineStageStepParameters stepParameters =
        pipelineStagePlanCreator.getStepParameter(config, yamlField, "planNodeId", PipelineVersion.V1, "acc");
    assertThat(stepParameters.getPipeline()).isEqualTo(PIPELINE);
    assertThat(stepParameters.getOrg()).isEqualTo(ORG);
    assertThat(stepParameters.getProject()).isEqualTo(PROJ);
    assertThat(stepParameters.getStageNodeId()).isEqualTo("planNodeId");
    assertThat(stepParameters.getPipelineInputsJsonNode()).isEqualTo(null);
    assertThat(stepParameters.getInputSetReferences().size()).isEqualTo(1);
    assertThat(stepParameters.getInputSetReferences().get(0)).isEqualTo("ref");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreatePlanForField() throws IOException {
    String yamlField = "type: Pipeline\n"
        + "__uuid: uuid\n"
        + "spec:\n"
        + "  org: org\n"
        + "  project: project\n"
        + "  pipeline: childPipeline\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField)
                                  .build();
    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true);

    assertThat(SecurityContextBuilder.getPrincipal()).isNull();

    assertThatThrownBy(
        () -> pipelineStagePlanCreator.createPlanForField(ctx, YamlUtils.readTreeWithDefaultObjectMapper(yamlField)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Child pipeline does not exists childPipeline ");

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true);

    PlanCreationResponse response =
        pipelineStagePlanCreator.createPlanForField(ctx, YamlUtils.readTreeWithDefaultObjectMapper(yamlField));
    assertThat(SecurityContextBuilder.getPrincipal()).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();
    PlanNode planNode = response.getPlanNode();
    assertThat(planNode.getName()).isEqualTo(pipelineStageYamlField.getNodeName());
    assertThat(planNode.getIdentifier()).isEqualTo(pipelineStageYamlField.getId());
    assertThat(planNode.getGroup()).isEqualTo(StepCategory.STAGE.name());
    assertThat(planNode.getFacilitatorObtainments().get(0).getType().getType())
        .isEqualTo(OrchestrationFacilitatorType.ASYNC);
    assertThat(planNode.getAdviserObtainments()).isNotNull();
    assertThat(planNode.getAdviserObtainments())
        .isEqualTo(PlanCreatorUtilsV1.getAdviserObtainmentsForStage(kryoSerializer, null));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions() {
    assertThat(pipelineStagePlanCreator.getSupportedYamlVersions()).isEqualTo(Set.of(PipelineVersion.V1));
  }
}
