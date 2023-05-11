/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.opa.service;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.opaclient.model.PipelineOpaEvaluationContext;
import io.harness.opaclient.model.UserOpaEvaluationContext;
import io.harness.pms.helpers.CurrentUserHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.rule.Owner;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import java.io.IOException;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSOpaServiceImplTest extends PipelineServiceTestBase {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";

  @Mock private PMSPipelineService pmsPipelineService;
  @Mock private PMSExecutionService pmsExecutionSummaryService;
  @Mock private CurrentUserHelper currentUserHelper;
  @InjectMocks private PMSOpaServiceImpl pmsOpaService;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetPipelineContextNoPipelineEntityNotPresent() {
    when(pmsPipelineService.getPipeline(any(), any(), any(), any(), eq(false), eq(false))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> pmsOpaService.getPipelineContext(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("The given pipeline id [%s] does not exist", PIPELINE_ID));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetPipelineContextNoPipelineEntityPresent() throws IOException {
    Principal principal = new UserPrincipal("1", "e", "u", "acc");
    PipelineOpaEvaluationContext pipelineOpaEvaluationContext =
        PipelineOpaEvaluationContext.builder()
            .pipeline(null)
            .user(UserOpaEvaluationContext.builder().email("e").name("1").build())
            .action("")
            .build();
    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(principal);
    when(pmsPipelineService.getPipeline(any(), any(), any(), any(), eq(false), eq(false)))
        .thenReturn(Optional.of(PipelineEntity.builder().build()));
    assertThat(pmsOpaService.getPipelineContext(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", "")).isNotNull();
    assertThat(pmsOpaService.getPipelineContext(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, "", ""))
        .isEqualTo(pipelineOpaEvaluationContext);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetPipelineContextFromExecution() throws IOException {
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryEntity(any(), any(), any(), any(), eq(false)))
        .thenReturn(PipelineExecutionSummaryEntity.builder().pipelineIdentifier(PIPELINE_ID).build());
    Principal principal = new UserPrincipal("1", "e", "u", "acc");
    PipelineOpaEvaluationContext pipelineOpaEvaluationContext =
        PipelineOpaEvaluationContext.builder()
            .pipeline(null)
            .user(UserOpaEvaluationContext.builder().email("e").name("1").build())
            .action("")
            .build();
    when(currentUserHelper.getPrincipalFromSecurityContext()).thenReturn(principal);
    when(pmsPipelineService.getPipeline(any(), any(), any(), any(), eq(false), eq(false)))
        .thenReturn(Optional.of(PipelineEntity.builder().build()));
    assertThat(pmsOpaService.getPipelineContextFromExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, ""))
        .isNotNull();
    assertThat(pmsOpaService.getPipelineContextFromExecution(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, ""))
        .isEqualTo(pipelineOpaEvaluationContext);
  }
}
