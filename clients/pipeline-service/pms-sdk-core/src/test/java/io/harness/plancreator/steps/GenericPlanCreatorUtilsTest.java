/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SANDESH_SALUNKHE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.advisers.pipelinerollback.OnFailPipelineRollbackParameters;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.NGFailureActionType;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.util.Strings;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GenericPlanCreatorUtilsTest extends CategoryTest {
  @Mock private YamlField currentYamlField;
  @Mock private YamlField parentYamlField;
  @Mock FailureStrategyActionConfig failureStrategyActionConfig;
  private static final String INVALID_REQUEST_EXCEPTION_MESSAGE =
      "failureStrategyActionConfig Failure action doesn't have corresponding RepairAction Code.";

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetStageNodeIdWhenStageNodeDoesNotExist() {
    YamlNode currentNode = mock(YamlNode.class);
    when(currentYamlField.getNode()).thenReturn(currentNode);
    when(currentNode.getUuid()).thenReturn(Strings.EMPTY);
    when(YamlUtils.findParentNode(currentYamlField.getNode(), STAGE)).thenReturn(null);
    String result = GenericPlanCreatorUtils.getStageNodeId(currentYamlField);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetStageNodeIdWhenParentNodeDoesNotExist() {
    YamlNode currentNode = mock(YamlNode.class);
    when(currentYamlField.getNode()).thenReturn(currentNode);
    when(currentNode.getUuid()).thenReturn("12345");
    when(currentNode.getParentNode()).thenReturn(null);
    when(YamlUtils.getGivenYamlNodeFromParentPath(currentNode, "PARENT")).thenReturn(null);
    when(YamlUtils.findParentNode(currentYamlField.getNode(), STAGE)).thenReturn(null);

    String result = GenericPlanCreatorUtils.getStageNodeId(currentYamlField);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetStageNodeIdWhenStageNodeExists() {
    YamlNode currentNode = mock(YamlNode.class);
    when(currentYamlField.getNode()).thenReturn(currentNode);
    when(currentNode.getUuid()).thenReturn("12345");
    YamlNode parentNode = mock(YamlNode.class);
    when(currentNode.getParentNode()).thenReturn(parentNode);

    List<YamlField> fields = Collections.singletonList(parentYamlField);
    when(parentNode.fields()).thenReturn(fields);
    when(parentYamlField.getName()).thenReturn(STAGE);
    when(parentYamlField.getNode()).thenReturn(parentNode);

    JsonNode jsonNode = new TextNode("12345");
    when(currentNode.getCurrJsonNode()).thenReturn(jsonNode);
    when(currentNode.toString()).thenReturn("12345");
    when(parentNode.getCurrJsonNode()).thenReturn(jsonNode);
    when(parentNode.toString()).thenReturn("12345");
    when(parentNode.getUuid()).thenReturn("12345");

    String result = GenericPlanCreatorUtils.getStageNodeId(currentYamlField);
    assertThat(result).isEqualTo("12345");
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetStageOrParallelNodeIdWhenStageNodeDoesNotExist() {
    YamlNode currentNode = mock(YamlNode.class);
    when(currentYamlField.getNode()).thenReturn(currentNode);
    when(currentNode.getUuid()).thenReturn(Strings.EMPTY);
    when(YamlUtils.findParentNode(currentYamlField.getNode(), STAGE)).thenReturn(null);

    String result = GenericPlanCreatorUtils.getStageOrParallelNodeId(currentYamlField);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetStageOrParallelNodeIdWhenParallelNodeDoesNotExist() {
    YamlNode currentNode = mock(YamlNode.class);
    when(currentYamlField.getNode()).thenReturn(currentNode);
    when(currentNode.getUuid()).thenReturn("12345");
    YamlNode stageNode = mock(YamlNode.class);
    doReturn("12345").when(stageNode).getUuid();
    YamlNode parentNode = mock(YamlNode.class);
    when(currentNode.getParentNode()).thenReturn(parentNode);

    List<YamlField> fields = Collections.singletonList(parentYamlField);
    when(parentNode.fields()).thenReturn(fields);
    when(parentYamlField.getName()).thenReturn(STAGE);
    when(parentYamlField.getNode()).thenReturn(parentNode);

    JsonNode jsonNode = new TextNode("12345");
    when(currentNode.getCurrJsonNode()).thenReturn(jsonNode);
    when(currentNode.toString()).thenReturn("12345");
    when(stageNode.getCurrJsonNode()).thenReturn(jsonNode);
    doReturn("12345").when(parentNode).getUuid();
    when(parentNode.getCurrJsonNode()).thenReturn(jsonNode);
    when(parentNode.toString()).thenReturn("12345");
    when(parentNode.getUuid()).thenReturn("12345");

    String result = GenericPlanCreatorUtils.getStageOrParallelNodeId(currentYamlField);
    assertThat(result).isEqualTo(stageNode.getUuid());
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForIGNORECase() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.IGNORE);
    RepairActionCode result = GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    assertThat(result).isEqualTo(RepairActionCode.IGNORE);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetRollbackStepsNodeId() {
    YamlNode currentNode = mock(YamlNode.class);
    YamlField rollbackStepsField = mock(YamlField.class);
    when(currentNode.getField(ROLLBACK_STEPS)).thenReturn(rollbackStepsField);
    YamlNode rollbackStepsNode = mock(YamlNode.class);
    when(rollbackStepsField.getNode()).thenReturn(rollbackStepsNode);
    when(rollbackStepsNode.getUuid()).thenReturn("12345");
    String result = GenericPlanCreatorUtils.getRollbackStepsNodeId(currentNode);
    assertThat(result).isEqualTo("12345");
    verify(currentNode, times(1)).getField(ROLLBACK_STEPS);
    verify(rollbackStepsField, times(1)).getNode();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetRollbackStepsNodeIdWithNonNullCurrentNodeAndNullRollbackStepsField() {
    YamlNode currentNode = mock(YamlNode.class);
    when(currentNode.getField(ROLLBACK_STEPS)).thenReturn(null);
    String result = GenericPlanCreatorUtils.getRollbackStepsNodeId(currentNode);
    assertThat(result).isNull();
    verify(currentNode, times(1)).getField(ROLLBACK_STEPS);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testGetRollbackStepsNodeIdWithNullCurrentNode() {
    String result = GenericPlanCreatorUtils.getRollbackStepsNodeId(null);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForMARK_AS_SUCCESSCase() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.MARK_AS_SUCCESS);
    RepairActionCode result = GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    assertThat(result).isEqualTo(RepairActionCode.MARK_AS_SUCCESS);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForABORTCase() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.ABORT);
    RepairActionCode result = GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    assertThat(result).isEqualTo(RepairActionCode.END_EXECUTION);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForSTAGE_ROLLBACKCase() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.STAGE_ROLLBACK);
    RepairActionCode result = GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    assertThat(result).isEqualTo(RepairActionCode.STAGE_ROLLBACK);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForMANUAL_INTERVENTIONCase() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.MANUAL_INTERVENTION);
    RepairActionCode result = GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    assertThat(result).isEqualTo(RepairActionCode.MANUAL_INTERVENTION);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForRETRYCase() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.RETRY);
    RepairActionCode result = GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    assertThat(result).isEqualTo(RepairActionCode.RETRY);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForMARK_AS_FAILURECase() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.MARK_AS_FAILURE);
    RepairActionCode result = GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    assertThat(result).isEqualTo(RepairActionCode.MARK_AS_FAILURE);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForPIPELINE_ROLLBACKCase() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.PIPELINE_ROLLBACK);
    RepairActionCode result = GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    assertThat(result).isEqualTo(RepairActionCode.PIPELINE_ROLLBACK);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForInvalidRequest1() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.PROCEED_WITH_DEFAULT_VALUES);
    InvalidRequestException invalidRequestException = new InvalidRequestException(INVALID_REQUEST_EXCEPTION_MESSAGE);
    try {
      GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).isEqualTo(invalidRequestException.getMessage());
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testToRepairActionForInvalidRequest2() {
    IgnoreFailureActionConfig ignoreFailureActionConfig = IgnoreFailureActionConfig.builder().build();
    when(failureStrategyActionConfig.getType()).thenReturn(NGFailureActionType.STEP_GROUP_ROLLBACK);
    InvalidRequestException invalidRequestException = new InvalidRequestException(INVALID_REQUEST_EXCEPTION_MESSAGE);
    try {
      GenericPlanCreatorUtils.toRepairAction(failureStrategyActionConfig);
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).isEqualTo(invalidRequestException.getMessage());
    }
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testBuildOnFailPipelineRollbackParametersEmptyList() {
    Set<FailureType> failureTypes = new HashSet<>();
    OnFailPipelineRollbackParameters onFailPipelineRollbackParameters =
        OnFailPipelineRollbackParameters.builder().applicableFailureTypes(failureTypes).build();
    assertThat(onFailPipelineRollbackParameters).isNotNull();
    assertThat(onFailPipelineRollbackParameters.getApplicableFailureTypes().size()).isZero();
    OnFailPipelineRollbackParameters result =
        GenericPlanCreatorUtils.buildOnFailPipelineRollbackParameters(failureTypes);
    assertThat(result).isEqualTo(onFailPipelineRollbackParameters);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testBuildOnFailPipelineRollbackParametersNonEmptyList() {
    Set<FailureType> failureTypes = new HashSet<>();
    failureTypes.add(FailureType.UNKNOWN_FAILURE);
    OnFailPipelineRollbackParameters onFailPipelineRollbackParameters =
        OnFailPipelineRollbackParameters.builder().applicableFailureTypes(failureTypes).build();
    assertThat(onFailPipelineRollbackParameters).isNotNull();
    assertThat(onFailPipelineRollbackParameters.getApplicableFailureTypes().size()).isOne();
    OnFailPipelineRollbackParameters result =
        GenericPlanCreatorUtils.buildOnFailPipelineRollbackParameters(failureTypes);
    assertThat(result).isEqualTo(onFailPipelineRollbackParameters);
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testObtainNextSiblingField() {
    YamlNode currentNode = mock(YamlNode.class);
    when(currentYamlField.getNode()).thenReturn(currentNode);
    when(currentYamlField.getName()).thenReturn("current");
    List<String> possibleSiblingFieldNames = Arrays.asList(STEP, PARALLEL, STEP_GROUP);
    YamlField nextSiblingField = mock(YamlField.class);
    doReturn(nextSiblingField).when(currentNode).nextSiblingFromParentArray("current", possibleSiblingFieldNames);
    YamlField result = GenericPlanCreatorUtils.obtainNextSiblingField(currentYamlField);
    assertThat(nextSiblingField).isEqualTo(result);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetRollbackStageNodeId() throws IOException {
    String singleStageYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - __uuid: a0\n"
        + "      stage:\n"
        + "        __uuid: a1\n"
        + "        fieldA:\n"
        + "          __uuid: a2\n"
        + "          fieldB:\n"
        + "            __uuid: a3\n"
        + "            fieldC:\n"
        + "              __uuid: a4\n"
        + "              fieldD: value";
    YamlField pipelineYamlField = YamlUtils.readTree(singleStageYaml);
    YamlField testField = pipelineYamlField.getNode()
                              .getFieldOrThrow("pipeline")
                              .getNode()
                              .getFieldOrThrow("stages")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getFieldOrThrow("stage")
                              .getNode()
                              .getFieldOrThrow("fieldA")
                              .getNode()
                              .getField("fieldB");
    assertThat(GenericPlanCreatorUtils.getRollbackStageNodeId(testField)).isEqualTo("a1_rollbackStage");

    String parallelStageYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - __uuid: a0\n"
        + "      parallel:\n"
        + "      - __uuid: a1\n"
        + "        stage:\n"
        + "          __uuid: a2\n"
        + "          fieldA:\n"
        + "            __uuid: a3\n"
        + "            fieldB:\n"
        + "              __uuid: a4\n"
        + "              fieldC:\n"
        + "                __uuid: a5\n"
        + "                fieldD: value";
    pipelineYamlField = YamlUtils.readTree(parallelStageYaml);
    testField = pipelineYamlField.getNode()
                    .getFieldOrThrow("pipeline")
                    .getNode()
                    .getFieldOrThrow("stages")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getFieldOrThrow("parallel")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getFieldOrThrow("stage")
                    .getNode()
                    .getFieldOrThrow("fieldA")
                    .getNode()
                    .getField("fieldB");
    assertThat(GenericPlanCreatorUtils.getRollbackStageNodeId(testField)).isEqualTo("a0parallel_rollbackStage");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testObtainNextSiblingFieldAtStageLevel() throws IOException {
    String singleStageYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - __uuid: a0\n"
        + "      stage:\n"
        + "         type: Pipeline\n"
        + "         __uuid: a1\n";
    YamlField pipelineYamlField = YamlUtils.readTree(singleStageYaml);
    YamlField pipelineStageNode = pipelineYamlField.getNode()
                                      .getField("pipeline")
                                      .getNode()
                                      .getField("stages")
                                      .getNode()
                                      .asArray()
                                      .get(0)
                                      .getField("stage");
    YamlField nextSiblingField = GenericPlanCreatorUtils.obtainNextSiblingFieldAtStageLevel(pipelineStageNode);
    assertThat(nextSiblingField).isNull();

    String multiStageYaml = "pipeline:\n"
        + "  stages:\n"
        + "     - __uuid: a0\n"
        + "       stage:\n"
        + "         type: Pipeline\n"
        + "         __uuid: a1\n"
        + "     - __uuid: a3\n"
        + "       stage:\n"
        + "         type: Deployment\n"
        + "         __uuid: a2\n";

    pipelineYamlField = YamlUtils.readTree(multiStageYaml);
    pipelineStageNode = pipelineYamlField.getNode()
                            .getField("pipeline")
                            .getNode()
                            .getField("stages")
                            .getNode()
                            .asArray()
                            .get(0)
                            .getField("stage");
    nextSiblingField = GenericPlanCreatorUtils.obtainNextSiblingFieldAtStageLevel(pipelineStageNode);
    assertThat(nextSiblingField.getNode().getUuid()).isEqualTo("a2");

    String parallelStageYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "         - __uuid: a0\n"
        + "           stage:\n"
        + "             type: Pipeline\n"
        + "         - __uuid: a3\n"
        + "           stage:\n"
        + "             type: Deployment\n";

    pipelineYamlField = YamlUtils.readTree(parallelStageYaml);
    pipelineStageNode = pipelineYamlField.getNode()
                            .getField("pipeline")
                            .getNode()
                            .getField("stages")
                            .getNode()
                            .asArray()
                            .get(0)
                            .getField("parallel")
                            .getNode()
                            .asArray()
                            .get(0)
                            .getField("stage");
    nextSiblingField = GenericPlanCreatorUtils.obtainNextSiblingFieldAtStageLevel(pipelineStageNode);
    assertThat(nextSiblingField).isNull();

    String parallelStageWithSeriesStage = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "         - __uuid: a0\n"
        + "           stage:\n"
        + "             type: Pipeline\n"
        + "         - __uuid: a1\n"
        + "           stage:\n"
        + "             type: Deployment\n"
        + "             __uuid: a4\n"
        + "         - __uuid: a6\n"
        + "           stage:\n"
        + "             type: Deployment\n"
        + "             __uuid: a7\n"
        + "    - stage:\n"
        + "       uuid: a5\n";

    pipelineYamlField = YamlUtils.readTree(parallelStageWithSeriesStage);
    pipelineStageNode = pipelineYamlField.getNode()
                            .getField("pipeline")
                            .getNode()
                            .getField("stages")
                            .getNode()
                            .asArray()
                            .get(0)
                            .getField("parallel")
                            .getNode()
                            .asArray()
                            .get(0)
                            .getField("stage");
    nextSiblingField = GenericPlanCreatorUtils.obtainNextSiblingFieldAtStageLevel(pipelineStageNode);
    assertThat(nextSiblingField).isNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCheckIfStepIsInParallelSection() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    URL testFile = classLoader.getResource("pipeline-with-stepGroup-inside-stepGroup.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    YamlField stepField = pipelineYamlField.getNode()
                              .getField("stages")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stage")
                              .getNode()
                              .getField("spec")
                              .getNode()
                              .getField("execution")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stepGroup")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stepGroup")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("step");

    assertThat(GenericPlanCreatorUtils.checkIfStepIsInParallelSection(stepField)).isFalse();

    testFile = classLoader.getResource("pipeline-with-stepGroup-inside-stepGroup-parallel.yaml");
    assertThat(testFile).isNotNull();
    pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    stepField = pipelineYamlField.getNode()
                    .getField("stages")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("stage")
                    .getNode()
                    .getField("spec")
                    .getNode()
                    .getField("execution")
                    .getNode()
                    .getField("steps")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("stepGroup")
                    .getNode()
                    .getField("steps")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("parallel")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("stepGroup")
                    .getNode()
                    .getField("steps")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("step");

    assertThat(GenericPlanCreatorUtils.checkIfStepIsInParallelSection(stepField)).isFalse();

    YamlField parallelYamlField = pipelineYamlField.getNode()
                                      .getField("stages")
                                      .getNode()
                                      .asArray()
                                      .get(0)
                                      .getField("stage")
                                      .getNode()
                                      .getField("spec")
                                      .getNode()
                                      .getField("execution")
                                      .getNode()
                                      .getField("steps")
                                      .getNode()
                                      .asArray()
                                      .get(1)
                                      .getField("parallel");

    YamlField innerStepGroupYamlField = parallelYamlField.getNode()
                                            .asArray()
                                            .get(0)
                                            .getField("stepGroup")
                                            .getNode()
                                            .getField("steps")
                                            .getNode()
                                            .asArray()
                                            .get(0)
                                            .getField("stepGroup");
    assertThat(GenericPlanCreatorUtils.checkIfStepIsInParallelSection(innerStepGroupYamlField)).isFalse();

    innerStepGroupYamlField = parallelYamlField.getNode()
                                  .asArray()
                                  .get(0)
                                  .getField("stepGroup")
                                  .getNode()
                                  .getField("steps")
                                  .getNode()
                                  .asArray()
                                  .get(1)
                                  .getField("stepGroup");

    assertThat(GenericPlanCreatorUtils.checkIfStepIsInParallelSection(innerStepGroupYamlField)).isFalse();

    YamlField parentStepGroupYamlField = parallelYamlField.getNode().asArray().get(0).getField("stepGroup");
    assertThat(GenericPlanCreatorUtils.checkIfStepIsInParallelSection(parentStepGroupYamlField)).isTrue();

    parentStepGroupYamlField = parallelYamlField.getNode().asArray().get(1).getField("stepGroup");
    assertThat(GenericPlanCreatorUtils.checkIfStepIsInParallelSection(parentStepGroupYamlField)).isTrue();

    testFile = classLoader.getResource("pipeline-with-step-parallel.yaml");
    assertThat(testFile).isNotNull();
    pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    stepField = pipelineYamlField.getNode()
                    .getField("stages")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("stage")
                    .getNode()
                    .getField("spec")
                    .getNode()
                    .getField("execution")
                    .getNode()
                    .getField("steps")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("stepGroup")
                    .getNode()
                    .getField("steps")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("parallel")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("stepGroup")
                    .getNode()
                    .getField("steps")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("parallel")
                    .getNode()
                    .asArray()
                    .get(0)
                    .getField("step");

    assertThat(GenericPlanCreatorUtils.checkIfStepIsInParallelSection(stepField)).isTrue();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCheckIfStepIsInParallelSectionNullCurrentField() {
    boolean result = GenericPlanCreatorUtils.checkIfStepIsInParallelSection(null);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCheckIfStepIsInParallelSectionNullCurrentNode() {
    doReturn(null).when(currentYamlField).getNode();
    boolean result = GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentYamlField);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCheckIfStepIsInParallelSectionWhenCurrentFieldIsNotInStepsAndRollbackSteps() {
    YamlNode currentNode = mock(YamlNode.class);
    doReturn(currentNode).when(currentYamlField).getNode();
    when(currentYamlField.checkIfParentIsParallel(STEPS)).thenReturn(false);
    when(currentYamlField.checkIfParentIsParallel(ROLLBACK_STEPS)).thenReturn(false);
    boolean result = GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentYamlField);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCheckIfStepIsInParallelSectionWithParallelParent() {
    YamlNode currentNode = mock(YamlNode.class);
    YamlNode parentNode = mock(YamlNode.class);
    when(currentYamlField.getNode()).thenReturn(currentNode);
    when(currentNode.getParentNode()).thenReturn(parentNode);
    when(currentYamlField.checkIfParentIsParallel(STEPS)).thenReturn(true);
    when(currentYamlField.checkIfParentIsParallel(ROLLBACK_STEPS)).thenReturn(false);
    boolean result = GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentYamlField);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SANDESH_SALUNKHE)
  @Category(UnitTests.class)
  public void testCheckIfStepIsInParallelSectionWithoutParallelParent() {
    YamlNode currentNode = mock(YamlNode.class);
    YamlNode parentNode = mock(YamlNode.class);
    when(currentYamlField.getNode()).thenReturn(currentNode);
    when(currentNode.getParentNode()).thenReturn(parentNode);
    when(parentNode.getParentNode()).thenReturn(null);
    when(currentYamlField.checkIfParentIsParallel(STEPS)).thenReturn(false);
    when(currentYamlField.checkIfParentIsParallel(ROLLBACK_STEPS)).thenReturn(true);
    boolean result = GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentYamlField);
    assertThat(result).isTrue();
  }
}
