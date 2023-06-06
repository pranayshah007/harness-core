/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.OrchestrationStepTypes;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.execution.PipelineStageResponseData;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.helper.PipelineStageHelper;
import io.harness.pms.pipelinestage.output.PipelineStageSweepingOutput;
import io.harness.pms.plan.execution.PipelineExecutor;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class PipelineStageStep implements AsyncExecutableWithRbac<PipelineStageStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(OrchestrationStepTypes.PIPELINE_STAGE).setStepCategory(StepCategory.STAGE).build();

  public static String NESTED_CHAINING_HINT = "Unable to run pipeline [%s]";
  public static String NESTED_CHAINING_ERROR =
      "The referred pipeline [%s] invokes a child pipeline, so it cannot be included within another pipeline. Nested Pipeline Chaining is not supported";

  @Inject private PipelineExecutor pipelineExecutor;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  @Inject private PipelineStageHelper pipelineStageHelper;

  @Inject private AccessControlClient client;

  @Inject private PMSExecutionService pmsExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject InterruptService interruptService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;

  @Override
  public Class<PipelineStageStepParameters> getStepParametersClass() {
    return PipelineStageStepParameters.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, PipelineStageStepParameters stepParameters, AsyncExecutableResponse executableResponse) {
    setSourcePrincipal(ambiance);
    // Setting interrupt config of parent pipeline while registering interrupt for child pipeline
    List<Interrupt> interrupts = interruptService.fetchAbortAllPlanLevelInterrupt(ambiance.getPlanExecutionId());
    if (isNotEmpty(interrupts)) {
      Interrupt interrupt = interrupts.get(0);
      if (executableResponse != null && isNotEmpty(executableResponse.getCallbackIdsList())) {
        pmsExecutionService.registerInterrupt(PlanExecutionInterruptType.ABORTALL, executableResponse.getCallbackIds(0),
            null, interrupt.getInterruptConfig());
      }
    }
  }

  public void setSourcePrincipal(Ambiance ambiance) {
    Principal principal = PmsSecurityContextGuardUtils.getPrincipalFromAmbiance(ambiance);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    SecurityContextBuilder.setContext(principal);
  }

  @Override
  public void validateResources(Ambiance ambiance, PipelineStageStepParameters stepParameters) {
    if (ambiance.getMetadata().getPipelineStageInfo().getHasParentPipeline()) {
      throw NestedExceptionUtils.hintWithExplanationException(
          String.format(NESTED_CHAINING_HINT, ambiance.getMetadata().getPipelineStageInfo().getIdentifier()),
          String.format(NESTED_CHAINING_ERROR, ambiance.getMetadata().getPipelineIdentifier()));
    }
    pipelineStageHelper.validateResource(client, ambiance, stepParameters);
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, PipelineStageStepParameters stepParameters, StepInputPackage inputPackage) {
    log.info(String.format("Starting Pipeline Stage with child pipeline %s", stepParameters.getPipeline()));

    PlanExecutionResponseDto responseDto = null;

    // set the principal for child
    setSourcePrincipal(ambiance);

    PipelineStageInfo info = prepareParentStageInfo(ambiance, stepParameters);
    responseDto = pipelineExecutor.runPipelineAsChildPipeline(ambiance.getSetupAbstractions().get("accountId"),
        stepParameters.getOrg(), stepParameters.getProject(), stepParameters.getPipeline(),
        ambiance.getMetadata().getModuleType(), stepParameters.getPipelineInputs(), false, false,
        stepParameters.getInputSetReferences(), info, ambiance.getMetadata().getIsDebug());

    if (responseDto == null) {
      throw new InvalidRequestException(
          String.format("Failed to execute child pipeline %s", stepParameters.getPipeline()));
    }
    // saving output for handleAsyncResponse
    sweepingOutputService.consume(ambiance, PipelineStageSweepingOutput.OUTPUT_NAME,
        PipelineStageSweepingOutput.builder().childExecutionId(responseDto.getPlanExecution().getUuid()).build(),
        StepCategory.STAGE.name());

    return AsyncExecutableResponse.newBuilder().addCallbackIds(responseDto.getPlanExecution().getUuid()).build();
  }

  public PipelineStageInfo prepareParentStageInfo(Ambiance ambiance, PipelineStageStepParameters stepParameters) {
    return PipelineStageInfo.newBuilder()
        .setExecutionId(ambiance.getPlanExecutionId())
        .setStageNodeId(stepParameters.getStageNodeId())
        .setHasParentPipeline(true)
        .setRunSequence(ambiance.getMetadata().getRunSequence())
        .setIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .setProjectId(ambiance.getSetupAbstractions().get("projectIdentifier"))
        .setOrgId(ambiance.getSetupAbstractions().get("orgIdentifier"))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, PipelineStageStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Handling Pipeline Stage Response");
    OptionalSweepingOutput sweepingOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(PipelineStageSweepingOutput.OUTPUT_NAME));
    if (!sweepingOutput.isFound() || !(sweepingOutput.getOutput() instanceof PipelineStageSweepingOutput)) {
      log.error("Child Pipeline details were not saved");
      return StepResponse.builder().status(Status.FAILED).build();
    }

    PipelineStageSweepingOutput pipelineStageSweepingOutput = (PipelineStageSweepingOutput) sweepingOutput.getOutput();

    NodeExecution nodeExecution;
    Ambiance childNodeAmbiance = null;
    Optional<NodeExecution> nodeExecutionOptional = nodeExecutionService.getPipelineNodeExecutionWithProjections(
        pipelineStageSweepingOutput.getChildExecutionId(), Collections.singleton(NodeExecutionKeys.ambiance));

    // NodeExecutionOptional can be empty when no node was executed in child execution
    if (nodeExecutionOptional.isPresent()) {
      nodeExecution = nodeExecutionOptional.get();
      childNodeAmbiance = nodeExecution.getAmbiance();
    }

    PipelineStageResponseData pipelineStageResponseData =
        (PipelineStageResponseData) responseDataMap.get(pipelineStageSweepingOutput.getChildExecutionId());
    return StepResponse.builder()
        .status(pipelineStageResponseData.getStatus())
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutputExpressionConstants.OUTPUT)
                         .outcome(pipelineStageHelper.resolveOutputVariables(
                             stepParameters.getOutputs().getValue(), childNodeAmbiance))
                         .build())
        .build();
  }
}
