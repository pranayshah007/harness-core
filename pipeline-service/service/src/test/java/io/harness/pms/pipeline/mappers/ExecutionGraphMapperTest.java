/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.plan.execution.PipelineExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ExecutionGraphMapperTest extends CategoryTest {
  String ACC_ID = "account";
  String PROJECT_ID = "project";
  String ORG_ID = "org";
  String PIPELINE_ID = "pipeline";
  String PLAN_EXECUTION_ID = "planId";

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetExecutionMetadata() {
    PipelineExecutionSummaryEntity entity = PipelineExecutionSummaryEntity.builder()
                                                .accountId(ACC_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .pipelineIdentifier(PIPELINE_ID)
                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                .build();
    Map<String, String> metadata = ExecutionGraphMapper.getMetadataMap(entity);
    assertThat(metadata.size()).isEqualTo(5);
    assertThat(metadata.get(PipelineExecutionSummaryKeys.pipelineIdentifier)).isEqualTo(PIPELINE_ID);
    assertThat(metadata.get(PipelineExecutionSummaryKeys.accountId)).isEqualTo(ACC_ID);
    assertThat(metadata.get(PipelineExecutionSummaryKeys.planExecutionId)).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(metadata.get(PipelineExecutionSummaryKeys.projectIdentifier)).isEqualTo(PROJECT_ID);
    assertThat(metadata.get(PipelineExecutionSummaryKeys.orgIdentifier)).isEqualTo(ORG_ID);
  }
}
