/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.pms.contracts.plan.ExecutionMode.POST_EXECUTION_ROLLBACK;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.exception.UnexpectedException;
import io.harness.execution.NodeExecution;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RollbackModeYamlTransformerTest extends CategoryTest {
  RollbackModeYamlTransformer rollbackModeYamlTransformer;
  @Mock NodeExecutionService nodeExecutionService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    rollbackModeYamlTransformer = new RollbackModeYamlTransformer(nodeExecutionService);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPostExecutionRollback() {
    doReturn(Collections.singletonList(NodeExecution.builder().identifier("s1").build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(anySet(), anySet());
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n";
    String transformedYaml =
        rollbackModeYamlTransformer.transformProcessedYaml(original, POST_EXECUTION_ROLLBACK, null);
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "    - stage:\n"
        + "        identifier: s1\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollback() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n";
    doReturn(Collections.singletonList(RetryStageInfo.builder().identifier("s1").name("s1").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionId("ogId");
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(original, PIPELINE_ROLLBACK, "ogId");
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollbackWithParallelStages() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s1\"\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n"
        + "  - stage:\n"
        + "      identifier: \"s3\"\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s4\"\n"
        + "    - stage:\n"
        + "        identifier: \"s5\"\n";
    doReturn(Arrays.asList(RetryStageInfo.builder().identifier("s1").name("s1").build(),
                 RetryStageInfo.builder().identifier("s2").name("s2").build(),
                 RetryStageInfo.builder().identifier("s3").name("s3").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionId("ogId");
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(original, PIPELINE_ROLLBACK, "ogId");
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s3\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: s1\n"
        + "        - stage:\n"
        + "            identifier: s2\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformProcessedYamlForPipelineRollbackWithOneParallelStageSkipped() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s1\"\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n";
    doReturn(List.of(RetryStageInfo.builder().identifier("s2").name("s2").build()))
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionId("ogId");
    String transformedYaml = rollbackModeYamlTransformer.transformProcessedYaml(original, PIPELINE_ROLLBACK, "ogId");
    String expected = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: s1\n"
        + "        - stage:\n"
        + "            identifier: s2\n";
    assertThat(transformedYaml).isEqualTo(expected);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleSerialAndParallelStage() throws IOException {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: \"s1\"\n"
        + "    - stage:\n"
        + "        identifier: \"s2\"\n"
        + "  - stage:\n"
        + "        identifier: s3\n";
    YamlField yamlField = YamlUtils.readTree(original);
    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(original).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to transform processed YAML while executing in Rollback Mode");
    }
    ArrayNode stagesList =
        (ArrayNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);

    // Handle Parallel Stage
    ArrayNode reversedStages = stagesList.deepCopy().removeAll();
    rollbackModeYamlTransformer.handleParallelStages(
        yamlField.fromYamlPath("pipeline").fromYamlPath("stages").getNode().asArray().get(0).getCurrJsonNode(),
        Collections.singletonList("s1"), reversedStages);
    assertThat(reversedStages.size()).isEqualTo(1);
    assertThat(reversedStages.get(0))
        .isEqualTo(pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES).get(0));

    // Handle Serial Stage
    rollbackModeYamlTransformer.handleSerialStage(
        yamlField.fromYamlPath("pipeline").fromYamlPath("stages").getNode().asArray().get(1).getCurrJsonNode(),
        Collections.singletonList("s3"), reversedStages);
    assertThat(reversedStages.size()).isEqualTo(2);
    assertThat(reversedStages.get(1))
        .isEqualTo(pipelineNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES).get(1));
  }
}