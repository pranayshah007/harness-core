/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.NGCommonUtilitiesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class StrategyStepTest extends NGCommonUtilitiesTestBase {
  private static final String CHILD_ID = generateUuid();

  @Mock MatrixConfigService matrixConfigService;
  @Mock ForLoopStrategyConfigService forLoopStrategyConfigService;
  @Mock ParallelismStrategyConfigService parallelismStrategyConfigService;
  @Mock EnforcementClientService enforcementClient;

  @InjectMocks StrategyStep strategyStep;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainChildrenForLoop() throws IOException {
    when(enforcementClient.isEnforcementEnabled()).thenReturn(false);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(2).getField("stage");

    YamlField strategyField = approvalStageYamlField.getNode().getField("strategy");
    StrategyConfig strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);
    StrategyStepParameters stepParameters =
        StrategyStepParameters.builder().childNodeId("childNodeId").strategyConfig(strategyConfig).build();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putSettingToValueMap(
                                 NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "false")
                             .build())
            .build();

    when(forLoopStrategyConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance))
        .thenReturn(new ArrayList<>());

    strategyStep.obtainChildren(ambiance, stepParameters, StepInputPackage.builder().build());

    verify(forLoopStrategyConfigService).fetchChildren(strategyConfig, "childNodeId", ambiance);

    strategyConfig.getRepeat().setMaxConcurrency(ParameterField.ofNull());

    when(forLoopStrategyConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance))
        .thenReturn(new ArrayList<>());

    strategyStep.obtainChildren(ambiance, stepParameters, StepInputPackage.builder().build());

    verify(forLoopStrategyConfigService, times(2)).fetchChildren(strategyConfig, "childNodeId", ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainChildrenParallelism() throws IOException {
    when(enforcementClient.isEnforcementEnabled()).thenReturn(false);

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(3).getField("stage");

    YamlField strategyField = approvalStageYamlField.getNode().getField("strategy");
    StrategyConfig strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);
    StrategyStepParameters stepParameters =
        StrategyStepParameters.builder().childNodeId("childNodeId").strategyConfig(strategyConfig).build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putSettingToValueMap(
                                 NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "false")
                             .build())
            .build();

    when(parallelismStrategyConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance))
        .thenReturn(new ArrayList<>());

    strategyStep.obtainChildren(ambiance, stepParameters, StepInputPackage.builder().build());

    verify(parallelismStrategyConfigService).fetchChildren(strategyConfig, "childNodeId", ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainChildrenMatrix() throws IOException {
    when(enforcementClient.isEnforcementEnabled()).thenReturn(false);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(0).getField("stage");

    YamlField strategyField = approvalStageYamlField.getNode().getField("strategy");
    StrategyConfig strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);
    StrategyStepParameters stepParameters =
        StrategyStepParameters.builder().childNodeId("childNodeId").strategyConfig(strategyConfig).build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putSettingToValueMap(
                                 NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "false")
                             .build())
            .build();

    when(matrixConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance)).thenReturn(new ArrayList<>());

    strategyStep.obtainChildren(ambiance, stepParameters, StepInputPackage.builder().build());

    verify(matrixConfigService).fetchChildren(strategyConfig, "childNodeId", ambiance);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStepParameterClass() throws IOException {
    assertThat(strategyStep.getStepParametersClass()).isEqualTo(StrategyStepParameters.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse() throws IOException {
    Ambiance ambiance = Ambiance.newBuilder().build();
    String childId2 = generateUuid();

    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put(CHILD_ID, StepResponseNotifyData.builder().nodeUuid(CHILD_ID).status(Status.FAILED).build())
            .put(childId2, StepResponseNotifyData.builder().nodeUuid(childId2).status(Status.SKIPPED).build())
            .build();
    StepResponse stepResponse = strategyStep.handleChildrenResponseInternal(
        ambiance, StrategyStepParameters.builder().build(), responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);

    responseDataMap = Collections.emptyMap();
    stepResponse = strategyStep.handleChildrenResponseInternal(
        ambiance, StrategyStepParameters.builder().build(), responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);

    responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put(CHILD_ID, StepResponseNotifyData.builder().nodeUuid(CHILD_ID).status(Status.SKIPPED).build())
            .build();
    stepResponse = strategyStep.handleChildrenResponseInternal(
        ambiance, StrategyStepParameters.builder().build(), responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
  }
}
