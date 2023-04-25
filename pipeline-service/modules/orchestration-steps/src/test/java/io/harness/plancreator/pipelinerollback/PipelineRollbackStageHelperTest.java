/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.pipelinerollback;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.LinkedHashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineRollbackStageHelperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAddPipelineRollbackStageDependency() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    String stages = "- stage: {}\n"
        + "- stage: {}\n"
        + "- stage: {}\n";
    YamlField stagesYamlField = YamlUtils.readTree(stages);
    PipelineRollbackStageHelper.addPipelineRollbackStageDependency(planCreationResponseMap, stagesYamlField);
    assertThat(planCreationResponseMap).hasSize(1);
    assertThat(planCreationResponseMap.values().stream().findFirst().isPresent()).isTrue();

    String uuid = planCreationResponseMap.keySet().stream().findFirst().get();
    PlanCreationResponse planCreationResponse = planCreationResponseMap.values().stream().findFirst().get();

    Dependencies dependencies = planCreationResponse.getDependencies();
    assertThat(dependencies.getDependenciesMap()).hasSize(1);
    assertThat(dependencies.getDependenciesMap().get(uuid)).isEqualTo("pipeline/stages/[3]/stage");

    YamlUpdates yamlUpdates = planCreationResponse.getYamlUpdates();
    assertThat(yamlUpdates.getFqnToYamlCount()).isEqualTo(1);
    assertThat(yamlUpdates.getFqnToYamlMap().containsKey("pipeline/stages/[3]")).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildPipelineRollbackStageJsonNode() {
    JsonNode jsonNode = PipelineRollbackStageHelper.buildPipelineRollbackStageJsonNode();
    assertThat(jsonNode.get("stage")).isNotNull();
    assertThat(jsonNode.get("stage").get("name").asText()).isEqualTo("Pipeline Rollback Stage");
    assertThat(jsonNode.get("stage").get("identifier")).isNotNull();
    assertThat(jsonNode.get("stage").get("__uuid")).isNotNull();
    assertThat(jsonNode.get("stage").get("type").asText()).isEqualTo("PipelineRollback");
    assertThat(jsonNode.get("stage").get("spec").asText()).isNotNull();
    assertThat(jsonNode.get("__uuid")).isNotNull();
  }
}