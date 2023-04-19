/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.rule.OwnerRule.VIKAS_S;

import static software.wings.graphql.datafetcher.execution.ResumeExecutionDataFetcher.ERROR_MESSAGE_EMPTY_APP_ID;
import static software.wings.graphql.datafetcher.execution.ResumeExecutionDataFetcher.ERROR_MESSAGE_EMPTY_PIPELINE_STAGE_NAME;
import static software.wings.graphql.datafetcher.execution.ResumeExecutionDataFetcher.ERROR_MESSAGE_EMPTY_WORKFLOW_EXECUTION_ID;
import static software.wings.graphql.datafetcher.execution.ResumeExecutionDataFetcher.ERROR_MESSAGE_EXECUTION_S_DOESN_T_EXIST;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.WorkflowExecution;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.execution.input.QLResumeExecutionInput;
import software.wings.graphql.schema.type.QLPipelineExecution.QLPipelineExecutionBuilder;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class ResumeExecutionDataFetcherTest extends AbstractDataFetcherTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String APPLICATION_ID = "APPLICATION_ID";

  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock PipelineExecutionController pipelineExecutionController;
  @Mock DeploymentAuthHandler deploymentAuthHandler;
  @Inject @InjectMocks private ResumeExecutionDataFetcher resumeExecutionDataFetcher;

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void validateInputOfMutateAndFetch() {
    String pipelineStageExecutionId = "pipelineStageExecutionId";
    String pipelineStageName = "pipelineStageName";
    {
      // Empty Application Name
      QLResumeExecutionInput parameter = QLResumeExecutionInput.builder()
                                             .pipelineExecutionId(pipelineStageExecutionId)
                                             //.applicationId(APPLICATION_ID)
                                             .pipelineStageName(pipelineStageName)
                                             .build();
      assertThatThrownBy(() -> resumeExecutionDataFetcher.mutateAndFetch(parameter, getMutationContext()))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage(ERROR_MESSAGE_EMPTY_APP_ID);
    }
    {
      // Empty Pipeline Stage Name
      QLResumeExecutionInput parameter = QLResumeExecutionInput.builder()
                                             .pipelineExecutionId(pipelineStageExecutionId)
                                             .applicationId(APPLICATION_ID)
                                             //.pipelineStageName(pipelineStageName)
                                             .build();
      assertThatThrownBy(() -> resumeExecutionDataFetcher.mutateAndFetch(parameter, getMutationContext()))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage(ERROR_MESSAGE_EMPTY_PIPELINE_STAGE_NAME);
    }
    {
      // Empty Pipeline Execution Id
      QLResumeExecutionInput parameter = QLResumeExecutionInput
                                             .builder()
                                             //.pipelineExecutionId(pipelineStageExecutionId)
                                             .applicationId(APPLICATION_ID)
                                             .pipelineStageName(pipelineStageName)
                                             .build();
      assertThatThrownBy(() -> resumeExecutionDataFetcher.mutateAndFetch(parameter, getMutationContext()))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage(ERROR_MESSAGE_EMPTY_WORKFLOW_EXECUTION_ID);
    }

    {
      // Non Existing Pipeline Execution.
      QLResumeExecutionInput parameter = QLResumeExecutionInput.builder()
                                             .pipelineExecutionId(pipelineStageExecutionId)
                                             .applicationId(APPLICATION_ID)
                                             .pipelineStageName(pipelineStageName)
                                             .build();
      when(workflowExecutionService.getWorkflowExecution(eq(APPLICATION_ID), eq(pipelineStageExecutionId)))
          .thenReturn(null);
      assertThatThrownBy(() -> resumeExecutionDataFetcher.mutateAndFetch(parameter, getMutationContext()))
          .isInstanceOf(GeneralException.class)
          .hasMessage(ERROR_MESSAGE_EXECUTION_S_DOESN_T_EXIST, pipelineStageExecutionId);
    }
  }

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testIntegrationWithDependencies() {
    String pipelineExecutionId = "pipelineExecutionId";
    String pipelineStageName = "pipelineStageName";
    QLResumeExecutionInput parameter = QLResumeExecutionInput.builder()
                                           .pipelineExecutionId(pipelineExecutionId)
                                           .applicationId(APPLICATION_ID)
                                           .pipelineStageName(pipelineStageName)
                                           .build();
    final WorkflowExecution previousExecution = WorkflowExecution.builder().build();
    final WorkflowExecution resumedExecution = WorkflowExecution.builder().build();
    when(workflowExecutionService.getWorkflowExecution(eq(APPLICATION_ID), eq(pipelineExecutionId)))
        .thenReturn(previousExecution);
    when(workflowExecutionService.triggerPipelineResumeExecution(
             eq(APPLICATION_ID), eq(pipelineStageName), eq(previousExecution)))
        .thenReturn(resumedExecution);
    resumeExecutionDataFetcher.mutateAndFetch(parameter, getMutationContext());
    // Verify Auth handler is called.
    verify(deploymentAuthHandler).authorize(eq(APPLICATION_ID), eq(previousExecution));
    // Verify PipelineExecutionController.populatePipelineExecution() is called with Resumed Execution.
    verify(pipelineExecutionController)
        .populatePipelineExecution(eq(resumedExecution), isA(QLPipelineExecutionBuilder.class));
  }

  private MutationContext getMutationContext() {
    return MutationContext.builder()
        .accountId(ACCOUNT_ID)
        .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
        .build();
  }
}
