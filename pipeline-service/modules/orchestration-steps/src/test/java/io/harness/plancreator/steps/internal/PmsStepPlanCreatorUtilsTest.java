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
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationStepsTestBase;
import io.harness.advisers.nextstep.NextStageAdviserParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.wait.WaitStepNode;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
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
    List<AdviserObtainment> list =
        PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(kryoSerializer, yamlField, false);
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
  @Owner(developers = OwnerRule.PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetNextStepAdviserObtainmentForParallelPipelineStage() throws IOException {
    String yaml = "---\n"
        + "stages:\n"
        + "  - parallel:\n"
        + "       - __uuid: uuid1\n"
        + "         stage:\n"
        + "           __uuid: uuid2\n"
        + "           type: Pipeline\n"
        + "       - stage:\n"
        + "           __uuid: uuid3\n"
        + "           type: Pipeline\n"
        + "  - stage:\n"
        + "       type: Deployment\n";

    YamlField yamlField = YamlUtils.readTree(yaml);
    YamlField stage = yamlField.getNode()
                          .getField("stages")
                          .getNode()
                          .asArray()
                          .get(0)
                          .getField("parallel")
                          .getNode()
                          .asArray()
                          .get(1)
                          .getField("stage");
    AdviserObtainment adviserObtainment = PmsStepPlanCreatorUtils.getNextStageAdviser(kryoSerializer, stage);
    assertNull(adviserObtainment);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN)
  @Category(UnitTests.class)
  public void testGetNextStageAdviserObtainment() throws IOException {
    String yaml = "stages:\n"
        + "- stage:\n"
        + "    identifier: s1\n"
        + "    type: Pipeline\n"
        + "    __uuid: uuid1\n"
        + "- stage:\n"
        + "    identifier: s2\n"
        + "    type: Pipeline\n"
        + "    __uuid: uuid2\n"
        + "- stage:\n"
        + "    identifier: prb-uuid3\n"
        + "    type: PipelineRollback\n"
        + "    __uuid: uuid3\n";
    YamlField yamlField = YamlUtils.readTree(yaml);

    YamlField stage1 = yamlField.getNode().getField("stages").getNode().asArray().get(0).getField("stage");
    AdviserObtainment adviserObtainment = PmsStepPlanCreatorUtils.getNextStageAdviser(kryoSerializer, stage1);
    assertThat(adviserObtainment.getType().getType()).isEqualTo("NEXT_STAGE");
    NextStageAdviserParameters adviserParameters =
        (NextStageAdviserParameters) kryoSerializer.asObject(adviserObtainment.getParameters().toByteArray());
    assertThat(adviserParameters.getNextNodeId()).isEqualTo("uuid2");
    assertThat(adviserParameters.getPipelineRollbackStageId()).isEqualTo("uuid3");

    YamlField stage2 = yamlField.getNode().getField("stages").getNode().asArray().get(1).getField("stage");
    adviserObtainment = PmsStepPlanCreatorUtils.getNextStageAdviser(kryoSerializer, stage2);
    assertThat(adviserObtainment.getType().getType()).isEqualTo("NEXT_STAGE");
    adviserParameters =
        (NextStageAdviserParameters) kryoSerializer.asObject(adviserObtainment.getParameters().toByteArray());
    assertThat(adviserParameters.getNextNodeId()).isNull();
    assertThat(adviserParameters.getPipelineRollbackStageId()).isEqualTo("uuid3");
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
        PmsStepPlanCreatorUtils.getAdviserObtainmentForFailureStrategy(kryoSerializer, yamlField, false, false);
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
}
