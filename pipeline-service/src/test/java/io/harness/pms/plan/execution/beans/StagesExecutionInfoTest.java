/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class StagesExecutionInfoTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToStagesExecutionMetadata() {
    String fullYaml = "full";
    String partYaml = "part";
    StagesExecutionInfo stagesExecutionInfo0 = StagesExecutionInfo.builder()
                                                   .isStagesExecution(false)
                                                   .pipelineYamlToRun(fullYaml)
                                                   .fullPipelineYaml(fullYaml)
                                                   .allowStagesExecution(true)
                                                   .build();
    StagesExecutionMetadata stagesExecutionMetadata0 = stagesExecutionInfo0.toStagesExecutionMetadata();
    assertThat(stagesExecutionMetadata0.isStagesExecution()).isFalse();
    assertThat(stagesExecutionMetadata0.getFullPipelineYaml()).isEqualTo(fullYaml);
    assertThat(stagesExecutionMetadata0.getStageIdentifiers()).isNull();
    assertThat(stagesExecutionMetadata0.getExpressionValues()).isNull();

    StagesExecutionInfo stagesExecutionInfo1 = StagesExecutionInfo.builder()
                                                   .isStagesExecution(true)
                                                   .pipelineYamlToRun(partYaml)
                                                   .fullPipelineYaml(fullYaml)
                                                   .stageIdentifiers(Collections.singletonList("s1"))
                                                   .allowStagesExecution(true)
                                                   .build();
    StagesExecutionMetadata stagesExecutionMetadata1 = stagesExecutionInfo1.toStagesExecutionMetadata();
    assertThat(stagesExecutionMetadata1.isStagesExecution()).isTrue();
    assertThat(stagesExecutionMetadata1.getFullPipelineYaml()).isEqualTo(fullYaml);
    assertThat(stagesExecutionMetadata1.getStageIdentifiers()).containsExactly("s1");
    assertThat(stagesExecutionMetadata1.getExpressionValues()).isNull();
  }
}
