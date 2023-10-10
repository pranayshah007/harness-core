/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PlanExecutionContextMapperTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCreatePlanExecutionContext() {
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid("execId")
                                              .setRunSequence(3)
                                              .setModuleType("cd")
                                              .setPipelineIdentifier("pipelineId")
                                              .setHarnessVersion("0")
                                              .build();
    PlanExecutionContext planExecutionContext = PlanExecutionContextMapper.toExecutionContext(executionMetadata);
    assertThat(planExecutionContext.getExecutionUuid()).isEqualTo("execId");
    assertThat(planExecutionContext.getRunSequence()).isEqualTo(3);
    assertThat(planExecutionContext.getPipelineIdentifier()).isEqualTo("pipelineId");
    assertThat(planExecutionContext.getHarnessVersion()).isEqualTo("0");
    assertThat(planExecutionContext.getProcessedYamlVersion()).isEqualTo("");
  }
}