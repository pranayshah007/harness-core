/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.OrchestrationStepsTestBase;
import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.wait.WaitStepNode;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsStepPlanCreatorUtilsTest extends OrchestrationStepsTestBase {
  @Inject private KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetAdviserObtainmentFromMetaData() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";
    YamlField yamlField = YamlUtils.readTree(yaml);
    List<AdviserObtainment> list = PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(kryoSerializer, yamlField);
    assertEquals(list.size(), 0);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetNextStepAdviserObtainment() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);
    AdviserObtainment adviserObtainment =
        PmsStepPlanCreatorUtils.getNextStepAdviserObtainment(kryoSerializer, yamlField);
    assertNull(adviserObtainment);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetOnSuccessAdviserObtainment() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);
    AdviserObtainment adviserObtainment =
        PmsStepPlanCreatorUtils.getOnSuccessAdviserObtainment(kryoSerializer, yamlField);
    assertNull(adviserObtainment);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetAdviserObtainmentForFailureStrategy() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);
    List<AdviserObtainment> adviserObtainment =
        PmsStepPlanCreatorUtils.getAdviserObtainmentForFailureStrategy(kryoSerializer, yamlField, false);
    assertEquals(adviserObtainment.size(), 0);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetName() {
    String name = "name";
    WaitStepNode waitStepNode = new WaitStepNode();
    waitStepNode.setName(name);
    assertEquals(PmsStepPlanCreatorUtils.getName(waitStepNode), name);
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
        PmsStepPlanCreatorUtils.getRollbackParameters(yamlField, failureTypeSet, RollbackStrategy.STAGE_ROLLBACK);
    assertEquals(onFailRollbackParameters.getStrategy(), RollbackStrategy.STAGE_ROLLBACK);
    assertEquals(onFailRollbackParameters.getApplicableFailureTypes().size(), 1);
    assertTrue(onFailRollbackParameters.getApplicableFailureTypes().contains(FailureType.AUTHENTICATION_FAILURE));
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
    Map<RollbackStrategy, String> map = PmsStepPlanCreatorUtils.getRollbackStrategyMap(yamlField);
    assertEquals(map.size(), 2);
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
    List<FailureStrategyConfig> lis = PmsStepPlanCreatorUtils.getFailureStrategies(yamlField.getNode());
    assertNull(lis);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testGetFieldFailureStrategies() throws IOException {
    String uuid = generateUuid();
    String yaml = "---\n"
        + "dummyAField: \"dummyA\"\n"
        + "__uuid: \"" + uuid + "\"\n";

    YamlField yamlField = YamlUtils.readTree(yaml);
    List<FailureStrategyConfig> lis = PmsStepPlanCreatorUtils.getFieldFailureStrategies(yamlField, "dummyField", false);
    assertEquals(lis.size(), 0);
  }
}
