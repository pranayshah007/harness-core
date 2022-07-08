/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.approval;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class ApprovalStagePlanCreatorTest extends CategoryTest {
  YamlField approvalStageYamlField;
  PlanCreationContext approvalStageContext;
  StageElementConfig approvalStageConfig;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("complex_pipeline.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    approvalStageYamlField = stageYamlNodes.get(0).getField("stage");
    approvalStageContext = PlanCreationContext.builder().currentField(approvalStageYamlField).build();
    approvalStageConfig = YamlUtils.read(approvalStageYamlField.getNode().toString(), StageElementConfig.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes() {
    YamlField specField = approvalStageYamlField.getNode().getField(YAMLFieldNameConstants.SPEC);
    assertThat(specField).isNotNull();
    String specUuid = specField.getNode().getUuid();
    YamlField executionField = specField.getNode().getField(YAMLFieldNameConstants.EXECUTION);
    assertThat(executionField).isNotNull();
    String executionUuid = executionField.getNode().getUuid();

    ApprovalStagePlanCreator approvalStagePlanCreator = new ApprovalStagePlanCreator();
    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        approvalStagePlanCreator.createPlanForChildrenNodes(approvalStageContext, approvalStageConfig);
    assertThat(planForChildrenNodes).isNotEmpty();
    assertThat(planForChildrenNodes).hasSize(2);
    assertThat(planForChildrenNodes.containsKey(specUuid)).isTrue();
    assertThat(planForChildrenNodes.containsKey(executionUuid)).isTrue();

    PlanCreationResponse specPlanCreationResponse = planForChildrenNodes.get(specUuid);
    assertThat(specPlanCreationResponse.getNodes()).hasSize(1);

    PlanCreationResponse executionPlanCreationResponse = planForChildrenNodes.get(executionUuid);
    assertThat(executionPlanCreationResponse.getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(executionPlanCreationResponse.getDependencies().getDependenciesMap().containsKey(executionUuid))
        .isTrue();
    assertThat(executionPlanCreationResponse.getDependencies().getDependenciesMap().get(executionUuid))
        .isEqualTo("pipeline/stages/[0]/stage/spec/execution");
  }
}
