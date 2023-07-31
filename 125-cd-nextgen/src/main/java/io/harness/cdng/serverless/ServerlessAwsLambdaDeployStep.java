/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.serverless.beans.ServerlessAwsLambdaStepExecutorParams;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessGitFetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessS3FetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExecutorParams;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.ServerlessNGException;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.ServerlessDeployConfig;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.delegate.task.serverless.request.ServerlessPrepareRollbackDataRequest;
import io.harness.delegate.task.serverless.response.ServerlessDeployResponse;
import io.harness.exception.ExceptionUtils;
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

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_SERVERLESS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServerlessAwsLambdaDeployStep
    extends TaskChainExecutableWithRollbackAndRbac implements ServerlessStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SERVERLESS_AWS_LAMBDA_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private final String SERVERLESS_AWS_LAMBDA_DEPLOY_COMMAND_NAME = "ServerlessAwsLambdaDeploy";
  private final String SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_COMMAND_NAME = "ServerlessAwsLambdaPrepareRollback";
  @Inject private ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private ServerlessAwsLambdaStepHelper serverlessAwsLambdaStepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return serverlessStepCommonHelper.startChainLink(ambiance, stepParameters, serverlessAwsLambdaStepHelper);
  }

  @Override
  public TaskChainResponse executeServerlessTask(ManifestOutcome serverlessManifestOutcome, Ambiance ambiance,
      StepElementParameters stepElementParameters, ServerlessExecutionPassThroughData executionPassThroughData,
      UnitProgressData unitProgressData, ServerlessStepExecutorParams serverlessStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    ServerlessAwsLambdaDeployStepParameters serverlessDeployStepParameters =
        (ServerlessAwsLambdaDeployStepParameters) stepElementParameters.getSpec();
    ServerlessAwsLambdaStepExecutorParams serverlessAwsLambdaStepExecutorParams =
        (ServerlessAwsLambdaStepExecutorParams) serverlessStepExecutorParams;
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    ServerlessArtifactConfig serverlessArtifactConfig = null;
    Optional<ArtifactsOutcome> artifactsOutcome = serverlessStepCommonHelper.getArtifactsOutcome(ambiance);

    Map<String, ServerlessArtifactConfig> sidecarServerlessArtifactConfigMap = new HashMap<>();
    if (artifactsOutcome.isPresent()) {
      if (artifactsOutcome.get().getPrimary() != null) {
        serverlessArtifactConfig =
            serverlessStepCommonHelper.getArtifactConfig(artifactsOutcome.get().getPrimary(), ambiance);
      }
      if (artifactsOutcome.get().getSidecars() != null) {
        artifactsOutcome.get().getSidecars().forEach((key, value) -> {
          if (value != null) {
            sidecarServerlessArtifactConfigMap.put(key, serverlessStepCommonHelper.getArtifactConfig(value, ambiance));
          }
        });
      }
    }

    ServerlessDeployConfig serverlessDeployConfig = serverlessStepCommonHelper.getServerlessDeployConfig(
        serverlessDeployStepParameters, serverlessAwsLambdaStepHelper);
    Map<String, Object> manifestParams = new HashMap<>();
    manifestParams.put(
        "manifestFileOverrideContent", serverlessAwsLambdaStepExecutorParams.getManifestFileOverrideContent());
    manifestParams.put("manifestFilePathContent", serverlessAwsLambdaStepExecutorParams.getManifestFilePathContent());
    ServerlessManifestConfig serverlessManifestConfig = serverlessStepCommonHelper.getServerlessManifestConfig(
        manifestParams, serverlessManifestOutcome, ambiance, serverlessAwsLambdaStepHelper);
    ServerlessDeployRequest serverlessDeployRequest =
        ServerlessDeployRequest.builder()
            .commandName(SERVERLESS_AWS_LAMBDA_DEPLOY_COMMAND_NAME)
            .accountId(accountId)
            .serverlessCommandType(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_DEPLOY)
            .serverlessInfraConfig(serverlessStepCommonHelper.getServerlessInfraConfig(infrastructureOutcome, ambiance))
            .serverlessDeployConfig(serverlessDeployConfig)
            .serverlessManifestConfig(serverlessManifestConfig)
            .serverlessArtifactConfig(serverlessArtifactConfig)
            .sidecarServerlessArtifactConfigs(sidecarServerlessArtifactConfigMap)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .manifestContent(serverlessAwsLambdaStepExecutorParams.getManifestFileOverrideContent())
            .build();
    return serverlessStepCommonHelper.queueServerlessTask(
        stepElementParameters, serverlessDeployRequest, ambiance, executionPassThroughData, true);
  }

  @Override
  public TaskChainResponse executeServerlessPrepareRollbackTask(ManifestOutcome serverlessManifestOutcome,
      Ambiance ambiance, StepElementParameters stepElementParameters,
      ServerlessStepPassThroughData serverlessStepPassThroughData, UnitProgressData unitProgressData,
      ServerlessStepExecutorParams serverlessStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = serverlessStepPassThroughData.getInfrastructureOutcome();
    ServerlessAwsLambdaStepExecutorParams serverlessAwsLambdaStepExecutorParams =
        (ServerlessAwsLambdaStepExecutorParams) serverlessStepExecutorParams;
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    Map<String, Object> manifestParams = new HashMap<>();
    manifestParams.put(
        "manifestFileOverrideContent", serverlessAwsLambdaStepExecutorParams.getManifestFileOverrideContent());
    manifestParams.put("manifestFilePathContent", serverlessAwsLambdaStepExecutorParams.getManifestFilePathContent());
    ServerlessManifestConfig serverlessManifestConfig = serverlessStepCommonHelper.getServerlessManifestConfig(
        manifestParams, serverlessManifestOutcome, ambiance, serverlessAwsLambdaStepHelper);
    ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest =
        ServerlessPrepareRollbackDataRequest.builder()
            .commandName(SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_COMMAND_NAME)
            .accountId(accountId)
            .serverlessCommandType(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK)
            .serverlessInfraConfig(serverlessStepCommonHelper.getServerlessInfraConfig(infrastructureOutcome, ambiance))
            .serverlessManifestConfig(serverlessManifestConfig)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .manifestContent(serverlessAwsLambdaStepExecutorParams.getManifestFileOverrideContent())
            .build();
    return serverlessStepCommonHelper.queueServerlessTask(
        stepElementParameters, serverlessPrepareRollbackDataRequest, ambiance, serverlessStepPassThroughData, false);
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return serverlessStepCommonHelper.executeNextLink(
        this, ambiance, stepParameters, passThroughData, responseSupplier, serverlessAwsLambdaStepHelper);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof ServerlessGitFetchFailurePassThroughData) {
      return serverlessStepCommonHelper.handleGitTaskFailure(
          (ServerlessGitFetchFailurePassThroughData) passThroughData);
    } else if (passThroughData instanceof ServerlessS3FetchFailurePassThroughData) {
      return serverlessStepCommonHelper.handleS3TaskFailure((ServerlessS3FetchFailurePassThroughData) passThroughData);
    } else if (passThroughData instanceof ServerlessStepExceptionPassThroughData) {
      return serverlessStepCommonHelper.handleStepExceptionFailure(
          (ServerlessStepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    ServerlessExecutionPassThroughData serverlessExecutionPassThroughData =
        (ServerlessExecutionPassThroughData) passThroughData;
    InfrastructureOutcome infrastructureOutcome = serverlessExecutionPassThroughData.getInfrastructure();
    ServerlessDeployResponse serverlessDeployResponse;
    try {
      serverlessDeployResponse = (ServerlessDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      ServerlessNGException serverlessException = ExceptionUtils.cause(ServerlessNGException.class, e);
      if (serverlessException == null) {
        log.error("Error while processing serverless task response: {}", e.getMessage(), e);
        return serverlessStepCommonHelper.handleTaskException(ambiance, serverlessExecutionPassThroughData, e);
      }
      log.error("Error while processing serverless task response: {}", e.getMessage(), e);
      return serverlessStepCommonHelper.handleTaskException(ambiance, serverlessExecutionPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(serverlessDeployResponse.getUnitProgressData().getUnitProgresses());
    if (serverlessDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return ServerlessStepCommonHelper.getFailureResponseBuilder(serverlessDeployResponse, stepResponseBuilder)
          .build();
    }
    List<ServerInstanceInfo> functionInstanceInfos = serverlessStepCommonHelper.getFunctionInstanceInfo(
        serverlessDeployResponse, serverlessAwsLambdaStepHelper, infrastructureOutcome.getInfrastructureKey());
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, functionInstanceInfos);

    return stepResponseBuilder.status(Status.SUCCEEDED).stepOutcome(stepOutcome).build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
