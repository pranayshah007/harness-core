/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.plan.execution.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MANKRIT;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.plan.execution.PipelineExecutor;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.model.PipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.model.PipelineExecuteResponseBody;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class ExecutionsApiImplTest extends CategoryTest {
  ExecutionsApiImpl executionsApi;
  @Mock PipelineExecutor pipelineExecutor;
  @Mock PMSExecutionService pmsExecutionService;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String pipeline = randomAlphabetic(10);
  String execution = randomAlphabetic(10);
  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    executionsApi = new ExecutionsApiImpl(
        pipelineExecutor, pmsExecutionService, pmsGitSyncHelper, accessControlClient, validateAndMergeHelper);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineExecuteInputSetRef() {
    List<String> inputSetRefs = Collections.singletonList("inputSetRef");
    PlanExecutionResponseDto responseDto =
        PlanExecutionResponseDto.builder()
            .planExecution(PlanExecution.builder().planId(execution).status(Status.ABORTED).startTs(1234L).build())
            .build();
    doReturn(responseDto)
        .when(pipelineExecutor)
        .runPipelineWithInputSetReferencesList(
            account, org, project, pipeline, null, inputSetRefs, null, null, Boolean.TRUE);
    PipelineExecuteRequestBody pipelineExecuteRequestBody = new PipelineExecuteRequestBody();
    pipelineExecuteRequestBody.setInputSetRefs(inputSetRefs);
    pipelineExecuteRequestBody.setNotifyExecutorOnly(Boolean.TRUE);
    Response response =
        executionsApi.executePipeline(pipelineExecuteRequestBody, org, project, pipeline, account, null);
    PipelineExecuteResponseBody pipelineExecuteResponseBody = (PipelineExecuteResponseBody) response.getEntity();
    assertEquals(execution, pipelineExecuteResponseBody.getSlug());
    assertEquals(PipelineExecuteResponseBody.StatusEnum.ABORTED, pipelineExecuteResponseBody.getStatus());
    assertEquals(1234L, (long) pipelineExecuteResponseBody.getStarted());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineExecuteRuntimeYAML() {
    String runTimeYAML = "runtimeYAML";
    PlanExecutionResponseDto responseDto =
        PlanExecutionResponseDto.builder()
            .planExecution(PlanExecution.builder().planId(execution).status(Status.ABORTED).startTs(1234L).build())
            .build();
    doReturn(responseDto)
        .when(pipelineExecutor)
        .runPipelineWithInputSetPipelineYaml(account, org, project, pipeline, null, runTimeYAML, false, Boolean.TRUE);
    PipelineExecuteRequestBody pipelineExecuteRequestBody = new PipelineExecuteRequestBody();
    pipelineExecuteRequestBody.setRuntimeYaml(runTimeYAML);
    pipelineExecuteRequestBody.setNotifyExecutorOnly(Boolean.TRUE);
    Response response =
        executionsApi.executePipeline(pipelineExecuteRequestBody, org, project, pipeline, account, null);
    PipelineExecuteResponseBody pipelineExecuteResponseBody = (PipelineExecuteResponseBody) response.getEntity();
    assertEquals(execution, pipelineExecuteResponseBody.getSlug());
    assertEquals(PipelineExecuteResponseBody.StatusEnum.ABORTED, pipelineExecuteResponseBody.getStatus());
    assertEquals(1234L, (long) pipelineExecuteResponseBody.getStarted());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testPipelineExecuteFail() {
    List<String> inputSetRefs = Collections.singletonList("inputSetRef");
    String runTimeYAML = "runtimeYAML";
    PipelineExecuteRequestBody pipelineExecuteRequestBody = new PipelineExecuteRequestBody();
    pipelineExecuteRequestBody.setNotifyExecutorOnly(Boolean.TRUE);
    try {
      executionsApi.executePipeline(pipelineExecuteRequestBody, org, project, pipeline, account, null);
    } catch (InvalidRequestException e) {
      assertEquals("Both InputSetReferences and RuntimeInputYAML are null, please pass one of them.", e.getMessage());
    }
    pipelineExecuteRequestBody.setInputSetRefs(inputSetRefs);
    pipelineExecuteRequestBody.setRuntimeYaml(runTimeYAML);
    try {
      executionsApi.executePipeline(pipelineExecuteRequestBody, org, project, pipeline, account, null);
    } catch (InvalidRequestException e) {
      assertEquals("Both InputSetReferences and RuntimeInputYAML are passed, please pass only either.", e.getMessage());
    }
  }
}