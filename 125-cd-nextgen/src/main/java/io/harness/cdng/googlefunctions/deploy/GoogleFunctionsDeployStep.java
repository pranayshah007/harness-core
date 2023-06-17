/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.googlefunctions.deploy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.googlefunctions.GoogleFunctionsEntityHelper;
import io.harness.cdng.googlefunctions.GoogleFunctionsHelper;
import io.harness.cdng.googlefunctions.GoogleFunctionsStepExceptionPassThroughData;
import io.harness.cdng.googlefunctions.GoogleFunctionsStepExecutor;
import io.harness.cdng.googlefunctions.GoogleFunctionsStepPassThroughData;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionStepOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionCommandTypeNG;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionDeployRequest;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionDeployRequest.GoogleFunctionDeployRequestBuilder;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionDeployResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class GoogleFunctionsDeployStep
    extends TaskChainExecutableWithRollbackAndRbac implements GoogleFunctionsStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GOOGLE_CLOUD_FUNCTIONS_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private final String GOOGLE_FUNCTION_DEPLOY_COMMAND_NAME = "DeployCloudFunction";

  @Inject private InstanceInfoService instanceInfoService;
  @Inject private GoogleFunctionsHelper googleFunctionsHelper;
  @Inject private GoogleFunctionsEntityHelper googleFunctionsEntityHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public TaskChainResponse executeTask(Ambiance ambiance, StepElementParameters stepParameters,
      GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = googleFunctionsStepPassThroughData.getInfrastructureOutcome();

    GoogleFunctionsDeployStepParameters googleFunctionsDeployStepParameters =
        (GoogleFunctionsDeployStepParameters) stepParameters.getSpec();

    GoogleFunctionDeployRequestBuilder googleFunctionDeployRequestBuilder =
        GoogleFunctionDeployRequest.builder()
            .googleFunctionCommandType(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_DEPLOY)
            .commandName(GOOGLE_FUNCTION_DEPLOY_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .googleFunctionInfraConfig(googleFunctionsHelper.getInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .googleFunctionDeployManifestContent(googleFunctionsStepPassThroughData.getManifestContent())
            .googleFunctionArtifactConfig(googleFunctionsEntityHelper.getArtifactConfig(
                googleFunctionsHelper.getArtifactOutcome(ambiance), AmbianceUtils.getNgAccess(ambiance)));

    if (googleFunctionsDeployStepParameters.getUpdateFieldMask().getValue() != null) {
      googleFunctionDeployRequestBuilder.updateFieldMaskContent(
          googleFunctionsDeployStepParameters.getUpdateFieldMask().getValue());
    }
    return googleFunctionsHelper.queueTask(stepParameters, googleFunctionDeployRequestBuilder.build(), ambiance,
        googleFunctionsStepPassThroughData, true, TaskType.GOOGLE_FUNCTION_DEPLOY_TASK);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return googleFunctionsHelper.executeNextLink(this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof GoogleFunctionsStepExceptionPassThroughData) {
      return googleFunctionsHelper.handleStepExceptionFailure(
          (GoogleFunctionsStepExceptionPassThroughData) passThroughData);
    }
    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData =
        (GoogleFunctionsStepPassThroughData) passThroughData;
    GoogleFunctionDeployResponse googleFunctionDeployResponse;
    try {
      googleFunctionDeployResponse = (GoogleFunctionDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing google function task response: {}", e.getMessage(), e);
      return googleFunctionsHelper.handleTaskException(ambiance, googleFunctionsStepPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(googleFunctionDeployResponse.getUnitProgressData().getUnitProgresses());
    if (googleFunctionDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return GoogleFunctionsHelper.getFailureResponseBuilder(googleFunctionDeployResponse, stepResponseBuilder).build();
    }
    GoogleFunctionStepOutcome googleFunctionDeployOutcome =
        googleFunctionsHelper.getGoogleFunctionStepOutcome(googleFunctionDeployResponse.getFunction());

    InfrastructureOutcome infrastructureOutcome = googleFunctionsStepPassThroughData.getInfrastructureOutcome();
    GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig =
        (GcpGoogleFunctionInfraConfig) googleFunctionsHelper.getInfraConfig(infrastructureOutcome, ambiance);
    List<ServerInstanceInfo> serverInstanceInfoList = googleFunctionsHelper.getServerInstanceInfo(
        googleFunctionDeployResponse, gcpGoogleFunctionInfraConfig, infrastructureOutcome.getInfrastructureKey());
    instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(googleFunctionDeployOutcome)
                         .build())
        .build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return googleFunctionsHelper.startChainLink(ambiance, stepParameters);
  }
}
