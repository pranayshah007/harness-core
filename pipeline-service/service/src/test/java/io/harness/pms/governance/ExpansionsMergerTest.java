/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.governance.ExpansionPlacementStrategy.APPEND;
import static io.harness.pms.contracts.governance.ExpansionPlacementStrategy.MOVE_UP;
import static io.harness.pms.contracts.governance.ExpansionPlacementStrategy.PARALLEL;
import static io.harness.pms.contracts.governance.ExpansionPlacementStrategy.REPLACE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.ExpansionResponseProto;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ExpansionsMergerTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testMergeExpansions() throws IOException {
    String simplePipeline = "pipeline:\n"
        + "  identifier: opaPipeline\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: first\n"
        + "        type: Expand\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "            - step:\n"
        + "                type: Expand\n"
        + "                identifier: jira\n"
        + "                spec:\n"
        + "                  expandThis: e1\n"
        + "                  expandAndRemove: e2\n"
        + "                  expandAndMoveUp: e3\n"
        + "                  replaceInline: e4\n"
        + "                timeout: 1d\n";
    ExpansionResponseProto resp0 =
        ExpansionResponseProto.newBuilder()
            .setFqn("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/expandThis")
            .setKey("et1")
            .setValue("{\"strategy\": \"parallel\"}")
            .setSuccess(true)
            .setPlacement(PARALLEL)
            .build();
    ExpansionResponseProto resp1 =
        ExpansionResponseProto.newBuilder()
            .setFqn("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/expandAndRemove")
            .setKey("et2")
            .setValue("{\"strategy\": \"replace\"}")
            .setSuccess(true)
            .setPlacement(REPLACE)
            .build();
    ExpansionResponseProto resp2 =
        ExpansionResponseProto.newBuilder()
            .setFqn("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/expandAndMoveUp")
            .setKey("et3")
            .setValue("{\"strategy\": \"moveUp\"}")
            .setSuccess(true)
            .setPlacement(MOVE_UP)
            .build();

    ExpansionResponseProto resp3 = ExpansionResponseProto.newBuilder()
                                       .setFqn("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec")
                                       .setKey("gitConfig")
                                       .setValue("{\"isMainBranch\": true}")
                                       .setSuccess(true)
                                       .setPlacement(APPEND)
                                       .build();

    ExpansionResponseProto resp4 =
        ExpansionResponseProto.newBuilder()
            .setFqn("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/replaceInline")
            .setKey("replaceInline")
            .setValue("{\"newKey\": \"newValue\"}")
            .setSuccess(true)
            .setPlacement(REPLACE)
            .build();

    List<ExpansionResponseProto> expansionResponseProtoList = Arrays.asList(resp0, resp1, resp2, resp3, resp4);
    ExpansionResponseBatch expansionResponseBatch =
        ExpansionResponseBatch.newBuilder().addAllExpansionResponseProto(expansionResponseProtoList).build();
    String expandedPipeline =
        ExpansionsMerger.mergeExpansions(simplePipeline, Collections.singleton(expansionResponseBatch));
    assertThat(expandedPipeline).isNotNull();
    YamlNode yamlNode = YamlUtils.readTree(expandedPipeline).getNode();
    assertThat(yamlNode.gotoPath("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/expandThis"))
        .isNotNull();
    assertThat(yamlNode.gotoPath("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/et1")).isNotNull();
    assertThat(yamlNode.gotoPath("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/expandAndRemove"))
        .isNull();
    assertThat(yamlNode.gotoPath("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/et2")).isNotNull();
    assertThat(yamlNode.gotoPath("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/et3")).isNotNull();
    assertThat(yamlNode.gotoPath("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/gitConfig")).isNotNull();
    assertThat(
        yamlNode.gotoPath("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/replaceInline").toString())
        .isEqualTo("{\"newKey\":\"newValue\"}");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetNewFQN() {
    String fqn = "pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/connectorRef";
    String key = "connector";
    String newFQN1 = ExpansionsMerger.getNewFQN(fqn, key, REPLACE);
    assertThat(newFQN1).isEqualTo("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/connector");
    String newFQN2 = ExpansionsMerger.getNewFQN(fqn, key, PARALLEL);
    assertThat(newFQN2).isEqualTo("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/connector");
    String newFQN3 = ExpansionsMerger.getNewFQN(fqn, key, MOVE_UP);
    assertThat(newFQN3).isEqualTo("pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/connector");
    String newFQN4 = ExpansionsMerger.getNewFQN(fqn, key, APPEND);
    assertThat(newFQN4).isEqualTo(
        "pipeline/stages/[0]/stage/spec/execution/steps/[0]/step/spec/connectorRef/connector");
  }
}
