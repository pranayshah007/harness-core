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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.plan.execution.PipelineExecutor;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.model.ExecutionsDetailsSummary;
import io.harness.spec.server.pipeline.model.PipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.model.PipelineExecuteResponseBody;
import io.harness.spec.server.pipeline.model.RuntimeYAMLTemplate;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
  PipelineExecutionSummaryEntity executionSummaryEntity;
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String pipeline = randomAlphabetic(10);
  String execution = randomAlphabetic(10);
  String inputSetYaml;

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename, e);
    }
  }

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    executionsApi = new ExecutionsApiImpl(
        pipelineExecutor, pmsExecutionService, pmsGitSyncHelper, accessControlClient, validateAndMergeHelper);
    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(account)
                                 .orgIdentifier(org)
                                 .projectIdentifier(project)
                                 .pipelineIdentifier(pipeline)
                                 .planExecutionId(execution)
                                 .name(execution)
                                 .runSequence(0)
                                 .build();
    String inputSetFilename = "inputSet1.yml";
    inputSetYaml = readFile(inputSetFilename);
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

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetExecutionDetail() {
    doReturn(executionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(account, org, project, execution, false);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    Response response = executionsApi.getExecutionDetails(org, project, execution, account);
    ExecutionsDetailsSummary executionsDetailsSummary = (ExecutionsDetailsSummary) response.getEntity();
    assertEquals(pipeline, executionsDetailsSummary.getPipeline());
    assertEquals(execution, executionsDetailsSummary.getSlug());
    assertEquals(0, (int) executionsDetailsSummary.getRunNumber());
  }
  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetRuntimeInputTemplate() {
    List<String> stages =
        Arrays.asList("using", "a", "list", "to", "ensure", "that", "this", "param", "is", "not", "ignored");
    doReturn(InputSetTemplateResponseDTOPMS.builder().inputSetTemplateYaml(inputSetYaml).build())
        .when(validateAndMergeHelper)
        .getInputSetTemplateResponseDTO(account, org, project, pipeline, Collections.emptyList());
    Response response = executionsApi.getRuntimeTemplate(org, project, pipeline, account, null, null);
    RuntimeYAMLTemplate yamlTemplate = (RuntimeYAMLTemplate) response.getEntity();
    assertEquals(yamlTemplate.getRuntimeYaml(), inputSetYaml);

    doReturn(InputSetTemplateResponseDTOPMS.builder().inputSetTemplateYaml(inputSetYaml).build())
        .when(validateAndMergeHelper)
        .getInputSetTemplateResponseDTO(account, org, project, pipeline, stages);
    Response response2 = executionsApi.getRuntimeTemplate(org, project, pipeline, account, stages, null);
    RuntimeYAMLTemplate yamlTemplate2 = (RuntimeYAMLTemplate) response.getEntity();
    assertEquals(yamlTemplate2.getRuntimeYaml(), inputSetYaml);
    verify(validateAndMergeHelper, times(1)).getInputSetTemplateResponseDTO(account, org, project, pipeline, stages);
  }
}