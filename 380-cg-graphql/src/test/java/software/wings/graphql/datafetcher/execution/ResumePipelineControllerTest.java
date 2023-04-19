/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Pipeline;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.graphql.schema.mutation.pipeline.input.QLRuntimeExecutionInputs;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.utils.JsonUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@OwnedBy(HarnessTeam.CDC)
public class ResumePipelineControllerTest extends WingsBaseTest {
  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock PipelineExecutionController pipelineExecutionController;
  @Mock PipelineService pipelineService;
  @Mock ExecutionController executionController;

  @Inject @InjectMocks ResumePipelineController resumePipelineController;

  private static final String PIPELINE_EXEC_ID = "ANWw9-y4QO67own792ab1g";
  private static final String PIPELINE_STAGE_ELEMENT_ID = "KZPqXENuRbKqkuISe07JAQ";
  private static final String APP_ID = "nCLN8c84SqWPr44sqg65JQ";
  @Test
  @Owner(developers = {DEEPAK_PUTHRAYA})
  @Category(UnitTests.class)
  public void testPipelineResumesCorrectly() {
    QLRuntimeExecutionInputs parameter =
        JsonUtils.readResourceFile("execution/graphql_continue_exec_params.json", QLRuntimeExecutionInputs.class);
    WorkflowExecution execution =
        JsonUtils.readResourceFile("execution/pipeline_execution_graphql.json", WorkflowExecution.class);
    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/pipeline_env_infra_service_runtime.json", Pipeline.class);
    String envId = "dlEk9oARQ6Oa4TDyZ62IKw";
    Map<String, String> wfVars = ImmutableMap.<String, String>builder()
                                     .put("service", "NA2uRPKLTqm9VU3dPENb-g")
                                     .put("infra", "Mq9q-Ch2QXCQlr7hGVMTIg")
                                     .put("env", "dlEk9oARQ6Oa4TDyZ62IKw")
                                     .build();
    when(workflowExecutionService.getWorkflowExecution(eq(APP_ID), eq(PIPELINE_EXEC_ID))).thenReturn(execution);
    when(pipelineService.readPipeline(eq(APP_ID), anyString(), eq(true))).thenReturn(pipeline);
    when(pipelineExecutionController.resolveEnvId(eq(execution), eq(pipeline), eq(parameter.getVariableInputs())))
        .thenReturn(envId);
    when(pipelineExecutionController.validateAndResolveRuntimePipelineStageVars(eq(pipeline),
             eq(parameter.getVariableInputs()), eq(envId), eq(new ArrayList<>()), eq(PIPELINE_STAGE_ELEMENT_ID),
             eq(false)))
        .thenReturn(wfVars);
    when(workflowExecutionService.fetchDeploymentMetadataRunningPipeline(
             eq(APP_ID), eq(wfVars), eq(false), eq(PIPELINE_EXEC_ID), eq(PIPELINE_STAGE_ELEMENT_ID)))
        .thenReturn(DeploymentMetadata.builder()
                        .artifactRequiredServiceIds(Lists.newArrayList("NA2uRPKLTqm9VU3dPENb-g"))
                        .build());

    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid("FAtIyyQiQOuVlGoLTV_yLg")
                            .withArtifactStreamId("CchJMhP6Tn6nFyca5lE1zw")
                            .withArtifactSourceName("deepakputhraya/python-hello")
                            .withMetadata(new ArtifactMetadata(
                                ImmutableMap.<String, String>builder()
                                    .put("image", "registry.hub.docker.com/deepakputhraya/python-hello:v3.0")
                                    .put("tag", "v3.0")
                                    .put("buildNo", "v3.0")
                                    .build()))
                            .withDisplayName("deepakputhraya/python-hello_v3.0_1709615")
                            .build();
    ArtifactVariable variable =
        ArtifactVariable.builder().entityId("NA2uRPKLTqm9VU3dPENb-g").value("FAtIyyQiQOuVlGoLTV_yLg").build();

    doAnswer((Answer<Void>) invocationOnMock -> {
      List<Artifact> artifactList = (List<Artifact>) invocationOnMock.getArguments()[3];
      List<ArtifactVariable> artifactVariables = (List<ArtifactVariable>) invocationOnMock.getArguments()[4];
      artifactList.add(artifact);
      artifactVariables.add(variable);
      return null;
    })
        .when(executionController)
        .getArtifactsFromServiceInputs(eq(parameter.getServiceInputs()), eq(APP_ID),
            eq(Lists.newArrayList("NA2uRPKLTqm9VU3dPENb-g")), eq(new ArrayList<>()), eq(new ArrayList<>()));

    when(pipelineExecutionController.resolvePipelineVariables(
             eq(pipeline), eq(parameter.getVariableInputs()), eq(envId), eq(new ArrayList<>()), eq(false)))
        .thenReturn(wfVars);

    ExecutionArgs args = ExecutionArgs.builder()
                             .stageName(execution.getStageName())
                             .workflowType(execution.getWorkflowType())
                             .workflowVariables(wfVars)
                             .artifactVariables(Lists.newArrayList(variable))
                             .artifacts(Lists.newArrayList(artifact))
                             .build();
    resumePipelineController.resumePipeline(parameter);
    verify(workflowExecutionService)
        .continuePipelineStage(eq(APP_ID), eq(PIPELINE_EXEC_ID), eq(PIPELINE_STAGE_ELEMENT_ID), eq(args));
  }
}
