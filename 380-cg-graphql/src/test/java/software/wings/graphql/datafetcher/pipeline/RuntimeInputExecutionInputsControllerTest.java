/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.pipeline;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.graphql.datafetcher.execution.PipelineExecutionController;
import software.wings.graphql.datafetcher.execution.RuntimeInputExecutionInputsController;
import software.wings.graphql.schema.query.QLExecutionInputsToResumePipelineQueryParams;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.utils.ArtifactType;
import software.wings.utils.JsonUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class RuntimeInputExecutionInputsControllerTest extends WingsBaseTest {
  @Mock ServiceResourceService serviceResourceService;
  @Mock PipelineExecutionController pipelineExecutionController;
  @Mock PipelineService pipelineService;
  @Mock AuthHandler authHandler;
  @Mock WorkflowExecutionService workflowExecutionService;

  private static ObjectMapper mapper;

  @Before
  public void beforeTest() {
    mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
  @Inject @InjectMocks RuntimeInputExecutionInputsController controller = new RuntimeInputExecutionInputsController();

  @Test
  @Owner(developers = {DEEPAK_PUTHRAYA})
  @Category(UnitTests.class)
  public void testQLExecutionInputReturnsCorrectServices() {
    WorkflowExecution pipelineExecution =
        JsonUtils.readResourceFile("execution/pipeline_execution.json", WorkflowExecution.class);
    when(workflowExecutionService.getWorkflowExecution(anyString(), anyString(), any(String[].class)))
        .thenReturn(pipelineExecution);
    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/pipeline.json", Pipeline.class);
    when(pipelineService.readPipeline(anyString(), anyString(), eq(true))).thenReturn(pipeline);
    when(pipelineExecutionController.resolveEnvId(eq(pipeline), anyList())).thenReturn("envId");
    when(pipelineExecutionController.validateAndResolvePipelineVariables(
             eq(pipeline), anyList(), eq("envId"), eq(new ArrayList<>()), eq(false)))
        .thenReturn(new HashMap<>());

    DeploymentMetadata metadata =
        DeploymentMetadata.builder().artifactRequiredServiceIds(Lists.newArrayList("SERVICE_ID")).build();
    PageResponse<Service> response = new PageResponse<>();
    response.add(Service.builder()
                     .uuid("serviceId")
                     .name("serviceName")
                     .appId("appId")
                     .description("description")
                     .artifactType(ArtifactType.DOCKER)
                     .deploymentType(DeploymentType.HELM)
                     .createdAt(0)
                     .createdBy(null)
                     .build());
    when(workflowExecutionService.fetchDeploymentMetadataRunningPipeline(
             anyString(), eq(new HashMap<>()), eq(false), eq("executionId"), eq("stageElementId")))
        .thenReturn(metadata);
    when(serviceResourceService.list(any(PageRequest.class), eq(false), eq(false), eq(false), any()))
        .thenReturn(response);

    JsonNode actual = JsonUtils.toJsonNode(controller.fetch(QLExecutionInputsToResumePipelineQueryParams.builder()
                                                                .applicationId("appId")
                                                                .pipelineStageElementId("stageElementId")
                                                                .pipelineExecutionId("executionId")
                                                                .variableInputs(new ArrayList<>())
                                                                .build(),
        "accountId"));
    JsonNode expected = JsonUtils.readResourceFile("execution/qlExecution_input_expected.json", JsonNode.class);
    assertEquals("QLInputs should be equal", expected.toString(), actual.toString());
  }
}
