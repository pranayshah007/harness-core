/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GenericPlanCreatorUtilsTest extends CategoryTest {
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
}
