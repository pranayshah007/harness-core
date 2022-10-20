/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.OrchestrationStepTypes;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.execution.PipelineStageResponseData;
import io.harness.exception.InvalidRequestException;
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
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class PipelineStageStep implements AsyncExecutableWithRbac<PipelineStageStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(OrchestrationStepTypes.PIPELINE_STAGE).setStepCategory(StepCategory.STAGE).build();

  @Inject private PipelineExecutor pipelineExecutor;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  @Inject private PipelineStageHelper pipelineStageHelper;

  @Inject private AccessControlClient client;

  @Override
  public Class<PipelineStageStepParameters> getStepParametersClass() {
    return PipelineStageStepParameters.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, PipelineStageStepParameters stepParameters, AsyncExecutableResponse executableResponse) {
    // TODO: to be handled
  }

  public void setSourcePrincipal(Ambiance ambiance) {
    Principal principal = PmsSecurityContextGuardUtils.getPrincipalFromAmbiance(ambiance);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    SecurityContextBuilder.setContext(principal);
  }

  @Override
  public void validateResources(Ambiance ambiance, PipelineStageStepParameters stepParameters) {
    pipelineStageHelper.validateResource(client, ambiance, stepParameters);
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, PipelineStageStepParameters stepParameters, StepInputPackage inputPackage) {
    log.info(String.format("Starting Pipeline Stage with child pipeline %s", stepParameters.getPipeline()));

    PlanExecutionResponseDto responseDto = null;
    setSourcePrincipal(ambiance);
    // TODO: pass the runtime yaml
    PipelineStageInfo info = prepareParentStageInfo(ambiance, stepParameters);
    responseDto = pipelineExecutor.runPipelineAsChildPipeline(ambiance.getSetupAbstractions().get("accountId"),
        stepParameters.getOrg(), stepParameters.getProject(), stepParameters.getPipeline(),
        ambiance.getMetadata().getModuleType(), "", false, false, stepParameters.getInputSetReferences(), info);

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

  private PipelineStageInfo prepareParentStageInfo(Ambiance ambiance, PipelineStageStepParameters stepParameters) {
    return PipelineStageInfo.newBuilder()
        .setExecutionId(ambiance.getPlanExecutionId())
        .setStageNodeExecutionId(ambiance.getStageExecutionId())
        .setHasParentPipeline(true)
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
    PipelineStageResponseData pipelineStageResponseData =
        (PipelineStageResponseData) responseDataMap.get(pipelineStageSweepingOutput.getChildExecutionId());
    // TODO: need to publish outcomes
    return StepResponse.builder().status(pipelineStageResponseData.getStatus()).build();
  }
}
