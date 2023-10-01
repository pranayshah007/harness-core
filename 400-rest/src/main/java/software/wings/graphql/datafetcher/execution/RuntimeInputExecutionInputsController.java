/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notBlankCheck;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.graphql.datafetcher.service.ServiceController;
import software.wings.graphql.schema.mutation.execution.input.QLVariableInput;
import software.wings.graphql.schema.query.QLExecutionInputsToResumePipelineQueryParams;
import software.wings.graphql.schema.type.QLService;
import software.wings.graphql.schema.type.execution.QLExecutionInputs;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class RuntimeInputExecutionInputsController {
  @Inject ServiceResourceService serviceResourceService;
  @Inject PipelineExecutionController pipelineExecutionController;
  @Inject PipelineService pipelineService;
  @Inject AuthHandler authHandler;
  @Inject WorkflowExecutionService workflowExecutionService;

  public QLExecutionInputs fetch(QLExecutionInputsToResumePipelineQueryParams parameters, String accountId) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      String appId = parameters.getApplicationId();
      notBlankCheck("Invalid app id", appId);
      notBlankCheck("Invalid pipeline execution", parameters.getPipelineExecutionId());
      notBlankCheck("Invalid pipeline stage ", parameters.getPipelineStageElementId());

      WorkflowExecution pipelineExecution = workflowExecutionService.getWorkflowExecution(appId,
          parameters.getPipelineExecutionId(), WorkflowExecutionKeys.executionArgs, WorkflowExecutionKeys.workflowId);
      notNullCheck("No pipeline execution for the given execution id " + parameters.getPipelineExecutionId(),
          pipelineExecution, USER);
      String pipelineId = pipelineExecution.getWorkflowId();
      Pipeline pipeline = pipelineService.readPipeline(appId, pipelineId, true);
      pipelineExecutionController.handleAuthentication(appId, pipeline);
      // Validate Required changes
      List<String> serviceIds = getArtifactNeededServices(appId, parameters, pipeline, pipelineExecution);
      if (isEmpty(serviceIds)) {
        return QLExecutionInputs.builder().serviceInputs(new ArrayList<>()).build();
      }
      PageRequest<Service> pageRequest = aPageRequest()
                                             .addFilter(ServiceKeys.appId, EQ, parameters.getApplicationId())
                                             .addFilter(ServiceKeys.accountId, EQ, accountId)
                                             .addFilter("_id", IN, serviceIds.toArray())
                                             .build();
      List<QLService> qlServices = serviceResourceService.list(pageRequest, false, false, false, null)
                                       .stream()
                                       .map(ServiceController::buildQLService)
                                       .collect(Collectors.toList());

      return QLExecutionInputs.builder().serviceInputs(qlServices).build();
    }
  }

  private List<String> getArtifactNeededServices(String appId, QLExecutionInputsToResumePipelineQueryParams params,
      Pipeline pipeline, WorkflowExecution execution) {
    String pipelineId = pipeline.getUuid();
    List<QLVariableInput> variableInputs = params.getVariableInputs();
    if (variableInputs == null) {
      variableInputs = new ArrayList<>();
    }
    String envId = pipelineExecutionController.resolveEnvId(execution, pipeline, variableInputs);
    List<String> extraVariables = new ArrayList<>();

    Map<String, String> workflowVariables = pipelineExecutionController.validateAndResolveRuntimePipelineStageVars(
        pipeline, variableInputs, envId, extraVariables, params.getPipelineStageElementId(), false);
    DeploymentMetadata finalDeploymentMetadata = workflowExecutionService.fetchDeploymentMetadataRunningPipeline(
        appId, workflowVariables, false, params.getPipelineExecutionId(), params.getPipelineStageElementId());
    if (finalDeploymentMetadata != null) {
      List<String> artifactNeededServiceIds = finalDeploymentMetadata.getArtifactRequiredServiceIds();
      if (isNotEmpty(artifactNeededServiceIds)) {
        return artifactNeededServiceIds;
      }
    }
    log.info("No Services requires artifact inputs for this pipeline: " + pipelineId);
    return new ArrayList<>();
  }
}
