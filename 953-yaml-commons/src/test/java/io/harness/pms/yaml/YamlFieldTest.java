/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class YamlFieldTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCheckIfParentIsParallel() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();

    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlField stage1Field =
        stagesNode.getNode().asArray().get(2).getField("parallel").getNode().asArray().get(0).getField("stage");

    // Since parallel stage, parent as stages will be true
    assertThat(stage1Field.checkIfParentIsParallel("stages")).isTrue();
    assertThat(stage1Field.checkIfParentIsParallel("steps")).isFalse();

    YamlField parallelStepField = stagesNode.getNode()
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
                                      .get(3)
                                      .getField("parallel")
                                      .getNode()
                                      .asArray()
                                      .get(0)
                                      .getField("step");

    // Since parallel step and its parent will have stages up the heirarchy hence returning true.
    assertThat(parallelStepField.checkIfParentIsParallel("stages")).isTrue();
    assertThat(parallelStepField.checkIfParentIsParallel("steps")).isTrue();

    YamlField normalStepField = stagesNode.getNode()
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
                                    .getField("step");
    assertThat(normalStepField.checkIfParentIsParallel("stages")).isFalse();
    assertThat(normalStepField.checkIfParentIsParallel("steps")).isFalse();

    YamlField normalStepFieldUnderParallelStage = stage1Field.getNode()
                                                      .getField("spec")
                                                      .getNode()
                                                      .getField("execution")
                                                      .getNode()
                                                      .getField("steps")
                                                      .getNode()
                                                      .asArray()
                                                      .get(0)
                                                      .getField("step");
    assertThat(normalStepFieldUnderParallelStage.checkIfParentIsParallel("stages")).isTrue();
    assertThat(normalStepFieldUnderParallelStage.checkIfParentIsParallel("steps")).isFalse();
  }
}
