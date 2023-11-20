/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix.v1;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.YUVRAJ;
import static io.harness.yaml.core.MatrixConstants.MATRIX_IDENTIFIER_POSTFIX_FOR_DUPLICATES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.strategy.v1.StrategyConfigV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.matrix.MatrixConfigServiceHelper;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class MatrixConfigServiceV1Test extends CategoryTest {
  private MatrixConfigServiceV1 matrixConfigService = new MatrixConfigServiceV1(new MatrixConfigServiceHelper());

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFetchChildren() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy-v1.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid);
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();

    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField StageYamlField = new YamlField(stageYamlNodes.get(0));

    YamlField strategyField = StageYamlField.getNode().getField("strategy");
    StrategyConfigV1 strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfigV1.class);

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putSettingToValueMap(
                                 NGPipelineSettingsConstant.ENABLE_MATRIX_FIELD_NAME_SETTING.getName(), "false")
                             .build())
            .build();
    List<ChildrenExecutableResponse.Child> children =
        matrixConfigService.fetchChildren(strategyConfig, "childNodeId", ambiance);
    assertThat(children.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testFetchChildrenHavingFewSameCombinations() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("matrix-with-repeating-combinations.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid);
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();

    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField StageYamlField = new YamlField(stageYamlNodes.get(0));

    YamlField strategyField = StageYamlField.getNode().getField("strategy");
    StrategyConfigV1 strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfigV1.class);

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
    assertThat(children.get(0).getStrategyMetadata().getMatrixMetadata().containsMatrixValues(
                   MATRIX_IDENTIFIER_POSTFIX_FOR_DUPLICATES))
        .isFalse();
    assertThat(children.get(1).getStrategyMetadata().getMatrixMetadata().containsMatrixValues(
                   MATRIX_IDENTIFIER_POSTFIX_FOR_DUPLICATES))
        .isTrue();
    assertThat(children.get(2).getStrategyMetadata().getMatrixMetadata().containsMatrixValues(
                   MATRIX_IDENTIFIER_POSTFIX_FOR_DUPLICATES))
        .isFalse();
    assertThat(children.get(3).getStrategyMetadata().getMatrixMetadata().containsMatrixValues(
                   MATRIX_IDENTIFIER_POSTFIX_FOR_DUPLICATES))
        .isTrue();
  }
}
