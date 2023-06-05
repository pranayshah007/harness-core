/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.plan.execution.beans.StagesExecutionInfo;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class StagesExecutionHelperTest extends CategoryTest {
  String pipelineYaml = "pipeline:\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: s1\n"
      + "      description: desc>\n"
      + "  - stage:\n"
      + "      identifier: s2\n"
      + "      description: desc\n";

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testThrowErrorIfAllStagesAreDeleted() {
    StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(pipelineYaml, Collections.singletonList("s2"));
    StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(pipelineYaml, Arrays.asList("s2", "s3", "s4"));
    assertThatThrownBy(
        () -> StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(pipelineYaml, Collections.singletonList("s3")))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStagesExecutionInfo() {
    String s2StageYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s2\n"
        + "        description: desc\n";
    List<String> stagesToRun = Collections.singletonList("s2");
    StagesExecutionInfo stagesExecutionInfo0 =
        StagesExecutionHelper.getStagesExecutionInfo(pipelineYaml, stagesToRun, null);
    assertThat(stagesExecutionInfo0.isStagesExecution()).isTrue();
    assertThat(stagesExecutionInfo0.getPipelineYamlToRun()).isEqualTo(s2StageYaml);
    assertThat(stagesExecutionInfo0.getFullPipelineYaml()).isEqualTo(pipelineYaml);
    assertThat(stagesExecutionInfo0.getExpressionValues()).isNull();

    Map<String, String> expressionValues = Collections.singletonMap("<+pipeline.stages.s1.name", "desc");
    StagesExecutionInfo stagesExecutionInfo1 =
        StagesExecutionHelper.getStagesExecutionInfo(pipelineYaml, stagesToRun, expressionValues);
    assertThat(stagesExecutionInfo1.isStagesExecution()).isTrue();
    assertThat(stagesExecutionInfo1.getPipelineYamlToRun()).isEqualTo(s2StageYaml);
    assertThat(stagesExecutionInfo1.getFullPipelineYaml()).isEqualTo(pipelineYaml);
    assertThat(stagesExecutionInfo1.getExpressionValues()).isEqualTo(expressionValues);
    assertThat(stagesExecutionInfo1.isAllowStagesExecution()).isEqualTo(true);
  }
}
