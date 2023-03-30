/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanCreatorUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetFieldFailureStrategies() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);
    List<FailureStrategyConfig> lis = PlanCreatorUtilsCommon.getFieldFailureStrategies(yamlField, "dummyField", false);
    assertEquals(lis.size(), 0);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetFailureStrategiesStepGroupInsideStepGroup() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-stepGroup-inside-stepGroup.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(pipelineYaml);
    YamlField stepField = yamlField.getNode()
                              .getField("pipeline")
                              .getNode()
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
    List<FailureStrategyConfig> lis = PlanCreatorUtilsCommon.getFieldFailureStrategies(stepField, "stepGroup", false);
    assertThat(lis.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetFailureStrategiesStepGroupInsideStepGroupWithDepthAs1() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-stepGroup-inside-stepGroup.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(pipelineYaml);
    YamlField stepField = yamlField.getNode()
                              .getField("pipeline")
                              .getNode()
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
    List<FailureStrategyConfig> lis =
        PlanCreatorUtilsCommon.getFieldFailureStrategies(stepField.getNode(), "stepGroup", false, 1);
    assertThat(lis.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetFailureStrategies() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);
    List<FailureStrategyConfig> lis = PlanCreatorUtilsCommon.getFailureStrategies(yamlField.getNode());
    assertNull(lis);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetRollbackStrategyMap() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);
    Map<RollbackStrategy, String> map = PlanCreatorUtilsCommon.getRollbackStrategyMap(yamlField);
    assertEquals(map.size(), 2);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetRollbackParameters() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);
    Set<FailureType> failureTypeSet = new HashSet<>();
    failureTypeSet.add(FailureType.AUTHENTICATION_FAILURE);

    OnFailRollbackParameters onFailRollbackParameters =
        PlanCreatorUtilsCommon.getRollbackParameters(yamlField, failureTypeSet, RollbackStrategy.STAGE_ROLLBACK);
    assertEquals(onFailRollbackParameters.getStrategy(), RollbackStrategy.STAGE_ROLLBACK);
    assertEquals(onFailRollbackParameters.getApplicableFailureTypes().size(), 1);
    assertTrue(onFailRollbackParameters.getApplicableFailureTypes().contains(FailureType.AUTHENTICATION_FAILURE));
  }
}
