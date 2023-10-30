/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class MatrixConfigServiceTest extends CategoryTest {
  MatrixConfigService matrixConfigService = new MatrixConfigService(new MatrixConfigServiceHelper());

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFetchChildren() throws IOException {
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

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putSettingToValueMap(
                                 NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "false")
                             .build())
            .build();

    List<ChildrenExecutableResponse.Child> children =
        matrixConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance);
    assertThat(children.size()).isEqualTo(8);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFetchChildren3Axis() throws IOException {
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

    YamlField approvalStageYamlField = stageYamlNodes.get(1).getField("stage");

    YamlField strategyField = approvalStageYamlField.getNode().getField("strategy");
    StrategyConfig strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putSettingToValueMap(
                                 NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "false")
                             .build())
            .build();

    List<ChildrenExecutableResponse.Child> children =
        matrixConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance);
    assertThat(children.size()).isEqualTo(24);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFetchChildrenComplexAxisWithRecaster() throws IOException {
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

    YamlField approvalStageYamlField = stageYamlNodes.get(10).getField("stage");

    YamlField strategyField = approvalStageYamlField.getNode().getField("strategy");
    StrategyConfig strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putSettingToValueMap(
                                 NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "false")
                             .build())
            .build();

    List<ChildrenExecutableResponse.Child> children =
        matrixConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance);
    assertThat(children.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testFetchChildrenWithNodeNames() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("matrix-nodename.yaml");
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

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putSettingToValueMap(
                                 NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "false")
                             .build())
            .build();
    List<ChildrenExecutableResponse.Child> children =
        matrixConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance);
    assertThat(children.size()).isEqualTo(6);
    assertThat(children.get(0).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("a");
    assertThat(children.get(1).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("a");
    assertThat(children.get(2).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("b");
    assertThat(children.get(3).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("b");
    assertThat(children.get(4).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("c");
    assertThat(children.get(5).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("c");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testFetchChildrenWithoutNodeNames() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("matrix-without-nodename.yaml");
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

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putSettingToValueMap(
                                 NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "false")
                             .build())
            .build();
    List<ChildrenExecutableResponse.Child> children =
        matrixConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance);
    assertThat(children.size()).isEqualTo(6);
    assertThat(children.get(0).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("");
    assertThat(children.get(1).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("");
    assertThat(children.get(2).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("");
    assertThat(children.get(3).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("");
    assertThat(children.get(4).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("");
    assertThat(children.get(5).getStrategyMetadata().getMatrixMetadata().getNodeName()).isEqualTo("");
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testResolveNodeName() throws IOException {
    Map<String, String> combination = new HashMap<>();
    combination.put("java", "a");
    combination.put("go", "world");
    Set<Map.Entry<String, String>> entries = combination.entrySet();

    String resolvedNodeName =
        matrixConfigService.matrixConfigServiceHelper.resolveNodeName(null, entries, combination, 0, 6);

    assertThat(resolvedNodeName).isEqualTo("a_world");
  }
}
