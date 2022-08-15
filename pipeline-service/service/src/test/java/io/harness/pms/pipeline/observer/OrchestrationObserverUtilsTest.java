/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.observer;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class OrchestrationObserverUtilsTest {
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyGetExecutedModulesInPipelineNullSafeElements() {
    Map<String, GraphLayoutNodeDTO> layoutNode = new HashMap<>();
    layoutNode.put(
        "keyA", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module("moduleA").build());
    layoutNode.put(
        "keyB", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module("moduleB").build());
    layoutNode.put("keyC",
        GraphLayoutNodeDTO.builder()
            .status(ExecutionStatus.SUCCESS)
            .skipInfo(SkipInfo.newBuilder().setEvaluatedCondition(true).build())
            .module(null)
            .build());
    layoutNode.put(
        "keyD", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module(null).build());
    layoutNode.put(
        "keyE", GraphLayoutNodeDTO.builder().status(ExecutionStatus.WAITING).skipInfo(null).module("moduleE").build());

    Set<String> modules = OrchestrationObserverUtils.getExecutedModulesInPipeline(
        PipelineExecutionSummaryEntity.builder().layoutNodeMap(layoutNode).build());
    assertThat(modules).hasSize(2);
    assertThat(modules).containsOnly("moduleA", "moduleB");
  }
}
