/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.plancreator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.gitsync.PmsGitSyncHelper;
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
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.core.failurestrategy.NGFailureActionTypeConstants;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStagePlanCreatorTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock PipelineStageHelper pipelineStageHelper;
  @Mock KryoSerializer kryoSerializer;
  @Mock PMSPipelineServiceImpl pmsPipelineService;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @InjectMocks PipelineStagePlanCreator pipelineStagePlanCreator;

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
  public void testGetStepParameter() throws IOException {
    String pipelineInputs = "---\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "type: \"Pipeline\"\n"
        + "stages:\n"
        + "   - stage:\n"
        + "       spec:\n"
        + "         pipeline: \"childPipeline\"\n"
        + "         org: \"org\"\n";

    YamlField yamlField = YamlUtils.readTree(pipelineInputs);
    PipelineStageConfig config = PipelineStageConfig.builder()
                                     .pipeline(PIPELINE)
                                     .org(ORG)
                                     .project(PROJ)
                                     .inputSetReferences(Collections.singletonList("ref"))
                                     .build();
    doReturn(null).when(pipelineStageHelper).getInputSetJsonNode(yamlField, PipelineVersion.V0);

    PipelineStageStepParameters stepParameters =
        pipelineStagePlanCreator.getStepParameter(config, yamlField, "planNodeId", PipelineVersion.V0, "acc");
    assertThat(stepParameters.getPipeline()).isEqualTo(PIPELINE);
    assertThat(stepParameters.getOrg()).isEqualTo(ORG);
    assertThat(stepParameters.getProject()).isEqualTo(PROJ);
    assertThat(stepParameters.getStageNodeId()).isEqualTo("planNodeId");
    assertThat(stepParameters.getPipelineInputsJsonNode()).isEqualTo(null);
    assertThat(stepParameters.getInputSetReferences().size()).isEqualTo(1);
    assertThat(stepParameters.getInputSetReferences().get(0)).isEqualTo("ref");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForField() throws IOException {
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"1w\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: uuid\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: parent_pipeline\n"
        + "          project: project\n"
        + "          inputSetReferences: []\n"
        + "          outputs: []\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    MockedStatic<YamlUtils> mockSettings = Mockito.mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), any())).thenReturn(pipelineStageYamlField.getNode());
    PlanCreationResponse response = pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);
    mockSettings.close();
    assertThat(SecurityContextBuilder.getPrincipal()).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();
    PlanNode planNode = response.getPlanNode();
    assertThat(planNode.getName()).isEqualTo(pipelineStageNode.getName());
    assertThat(planNode.getIdentifier()).isEqualTo(pipelineStageNode.getIdentifier());
    assertThat(planNode.getGroup()).isEqualTo(StepCategory.STAGE.name());
    assertThat(planNode.getFacilitatorObtainments().get(0).getType().getType())
        .isEqualTo(OrchestrationFacilitatorType.ASYNC);
    assertThat(planNode.getExpressionMode()).isEqualTo(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

    // Verifying the new Git Context For ChildPipeline
    verify(pmsGitSyncHelper, times(1)).getEntityGitDetailsFromBytes(any());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithFailureStrategy() throws IOException {
    String ignoreFailureYamlField = getFailureYamlField(NGFailureActionTypeConstants.IGNORE);

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(ignoreFailureYamlField);

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: parent_pipeline\n"
        + "          project: project\n"
        + "          inputSetReferences: []\n"
        + "          outputs: []\n";

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(ignoreFailureYamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    doReturn(new byte[9]).when(kryoSerializer).asBytes(any());
    PipelineStageNode stageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    MockedStatic<YamlUtils> mockSettings = Mockito.mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), any())).thenReturn(pipelineStageYamlField.getNode());
    pipelineStagePlanCreator.createPlanForField(ctx, stageNode);
    mockSettings.close();

    verify(pipelineStageHelper, times(1)).validateFailureStrategy(stageNode.getFailureStrategies());
  }

  @NotNull
  private String getFailureYamlField(String action) {
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"1w\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: uuid\n"
        + "  failureStrategies:\n"
        + "    - onFailure:\n"
        + "        errors:\n"
        + "           - AllErrors\n"
        + "        action:\n"
        + "           type: " + action + "\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";
    return yamlField;
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions() {
    assertThat(pipelineStagePlanCreator.getSupportedYamlVersions()).isEqualTo(Set.of(PipelineVersion.V0));
  }
}
