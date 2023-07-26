/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notBlankCheck;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.ArtifactVariable;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Pipeline;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.graphql.schema.mutation.pipeline.input.QLRuntimeExecutionInputs;
import software.wings.graphql.schema.mutation.pipeline.payload.QLContinueExecutionPayload;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ResumePipelineController {
  @Inject WorkflowExecutionService workflowExecutionService;
  @Inject PipelineExecutionController pipelineExecutionController;
  @Inject PipelineService pipelineService;
  @Inject ExecutionController executionController;

  public QLContinueExecutionPayload resumePipeline(QLRuntimeExecutionInputs parameter) {
    String appId = parameter.getApplicationId();
    notBlankCheck("Invalid app id", appId);
    notBlankCheck("Invalid pipeline execution", parameter.getPipelineExecutionId());
    notBlankCheck("Invalid pipeline stage ", parameter.getPipelineStageElementId());

    String[] fields = {WorkflowExecutionKeys.executionArgs, WorkflowExecutionKeys.stageName,
        WorkflowExecutionKeys.workflowId, WorkflowExecutionKeys.workflowType};
    WorkflowExecution execution =
        workflowExecutionService.getWorkflowExecution(appId, parameter.getPipelineExecutionId(), fields);
    notNullCheck("No execution found for the given application " + appId, execution, USER);
    String pipelineId = execution.getWorkflowId();
    Pipeline pipeline = pipelineService.readPipeline(appId, pipelineId, true);

    String envId = pipelineExecutionController.resolveEnvId(execution, pipeline, parameter.getVariableInputs());

    Map<String, String> workflowVariables =
        pipelineExecutionController.validateAndResolveRuntimePipelineStageVars(pipeline, parameter.getVariableInputs(),
            envId, new ArrayList<>(), parameter.getPipelineStageElementId(), false);
    DeploymentMetadata finalDeploymentMetadata = workflowExecutionService.fetchDeploymentMetadataRunningPipeline(
        appId, workflowVariables, false, parameter.getPipelineExecutionId(), parameter.getPipelineStageElementId());

    List<ArtifactVariable> artifactVariables = new ArrayList<>();
    List<Artifact> artifacts = new ArrayList<>();
    if (isEmpty(parameter.getServiceInputs()) && isNotEmpty(finalDeploymentMetadata.getArtifactRequiredServiceIds())) {
      throw new InvalidRequestException("Services requires but no service was provided", USER);
    }
    if (isNotEmpty(finalDeploymentMetadata.getArtifactRequiredServiceIds())) {
      executionController.getArtifactsFromServiceInputs(parameter.getServiceInputs(), appId,
          finalDeploymentMetadata.getArtifactRequiredServiceIds(), artifacts, artifactVariables);
    }
    ExecutionArgs executionArgs = ExecutionArgs.builder()
                                      .stageName(execution.getStageName())
                                      .workflowType(execution.getWorkflowType())
                                      .workflowVariables(pipelineExecutionController.resolvePipelineVariables(
                                          pipeline, parameter.getVariableInputs(), envId, new ArrayList<>(), false))
                                      .artifactVariables(artifactVariables)
                                      .artifacts(artifacts)
                                      .build();
    boolean status = workflowExecutionService.continuePipelineStage(
        execution.getAppId(), parameter.getPipelineExecutionId(), parameter.getPipelineStageElementId(), executionArgs);

    return QLContinueExecutionPayload.builder()
        .clientMutationId(parameter.getClientMutationId())
        .status(status)
        .build();
  }
}
