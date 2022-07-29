/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.serverless.beans.*;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.ServerlessNGException;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.ServerlessDeployConfig;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.delegate.task.serverless.request.ServerlessGenericRequest;
import io.harness.delegate.task.serverless.request.ServerlessPrepareRollbackDataRequest;
import io.harness.delegate.task.serverless.response.ServerlessDeployResponse;
import io.harness.delegate.task.serverless.response.ServerlessGenericResponse;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.shell.ShellExecutionData;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.shellscript.ShellScriptOutcome;
import io.harness.steps.shellscript.ShellScriptStepParameters;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServerlessAwsLambdaGenericStep
    extends TaskChainExecutableWithRollbackAndRbac implements ServerlessStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SERVERLESS_AWS_LAMBDA_GENERIC.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private final String SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_COMMAND_NAME = "ServerlessAwsLambdaPrepareRollback";
  private final String SERVERLESS_AWS_LAMBDA_GENERIC_COMMAND_NAME = "ServerlessAwsLambdaGeneric";
  @Inject private ServerlessGenericStepCommonHelper serverlessStepCommonHelper;
  @Inject private ServerlessAwsLambdaStepHelper serverlessAwsLambdaStepHelper;
  @Inject private ShellScriptHelperService shellScriptHelperService;

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
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      UnitProgressData unitProgressData, ServerlessStepExecutorParams serverlessStepExecutorParams) {
    ServerlessStepPassThroughData stepPassThroughData = (ServerlessStepPassThroughData) passThroughData;
    InfrastructureOutcome infrastructureOutcome = stepPassThroughData.getInfrastructureOutcome();
    ServerlessAwsLambdaGenericStepParameters serverlessDeployStepParameters =
        (ServerlessAwsLambdaGenericStepParameters) stepElementParameters.getSpec();
    ServerlessAwsLambdaStepExecutorParams serverlessAwsLambdaStepExecutorParams =
        (ServerlessAwsLambdaStepExecutorParams) serverlessStepExecutorParams;
    ShellScriptStepParameters shellScriptStepParameters = ShellScriptStepParameters.infoBuilder()
            .environmentVariables(serverlessDeployStepParameters.getEnvironmentVariables())
            .outputVariables(serverlessDeployStepParameters.getOutputVariables())
            .executionTarget(serverlessDeployStepParameters.getServerlessShellScriptSpec().getExecutionTarget())
            .shellType(serverlessDeployStepParameters.getServerlessShellScriptSpec().getShell())
            .onDelegate(serverlessDeployStepParameters.getServerlessShellScriptSpec().getOnDelegate())
            .delegateSelectors(serverlessDeployStepParameters.getDelegateSelectors())
            .source(serverlessDeployStepParameters.getServerlessShellScriptSpec().getSource())
            .uuid(serverlessDeployStepParameters.getServerlessShellScriptSpec().getUuid())
            .build();
    ShellScriptTaskParametersNG serverlessShellScriptTaskParameters =
            shellScriptHelperService.buildShellScriptTaskParametersNG(ambiance, shellScriptStepParameters);
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

    Map<String, Object> manifestParams = new HashMap<>();
    manifestParams.put(
        "manifestFileOverrideContent", serverlessAwsLambdaStepExecutorParams.getManifestFileOverrideContent());
    manifestParams.put("manifestFilePathContent", serverlessAwsLambdaStepExecutorParams.getManifestFilePathContent());
    ServerlessManifestConfig serverlessManifestConfig = serverlessStepCommonHelper.getServerlessManifestConfig(
        manifestParams, serverlessManifestOutcome, ambiance, serverlessAwsLambdaStepHelper);
    ServerlessGenericRequest serverlessGenericRequest =
        ServerlessGenericRequest.builder()
            .commandName(SERVERLESS_AWS_LAMBDA_GENERIC_COMMAND_NAME)
            .accountId(accountId)
            .serverlessCommandType(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_GENERIC)
            .serverlessInfraConfig(serverlessStepCommonHelper.getServerlessInfraConfig(infrastructureOutcome, ambiance))
            .shellScriptTaskParametersNG(serverlessShellScriptTaskParameters)
            .serverlessManifestConfig(serverlessManifestConfig)
            .serverlessArtifactConfig(serverlessArtifactConfig)
            .sidecarServerlessArtifactConfigs(sidecarServerlessArtifactConfigMap)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .manifestContent(serverlessAwsLambdaStepExecutorParams.getManifestFileOverrideContent())
            .build();
    return serverlessStepCommonHelper.queueServerlessTask(
        stepElementParameters, serverlessGenericRequest, ambiance, stepPassThroughData, true);
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
    } else if (passThroughData instanceof ServerlessStepExceptionPassThroughData) {
      return serverlessStepCommonHelper.handleStepExceptionFailure(
          (ServerlessStepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    try {
      StepResponseBuilder stepResponseBuilder = StepResponse.builder();
      ServerlessGenericResponse taskResponse = (ServerlessGenericResponse) responseDataSupplier.get();
      ServerlessAwsLambdaGenericStepParameters shellScriptStepParameters = (ServerlessAwsLambdaGenericStepParameters) stepParameters.getSpec();
      List<UnitProgress> unitProgresses = taskResponse.getUnitProgressData() == null
              ? emptyList()
              : taskResponse.getUnitProgressData().getUnitProgresses();
      stepResponseBuilder.unitProgressList(unitProgresses);

      stepResponseBuilder.status(StepUtils.getStepStatus(taskResponse.getCommandExecutionStatus()));

      FailureInfo.Builder failureInfoBuilder = FailureInfo.newBuilder();
      if (taskResponse.getErrorMessage() != null) {
        failureInfoBuilder.setErrorMessage(taskResponse.getErrorMessage());
      }
      stepResponseBuilder.failureInfo(failureInfoBuilder.build());

      if (taskResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
        ShellExecutionData commandExecutionData =
                (ShellExecutionData) taskResponse.getExecuteCommandResponse().getCommandExecutionData();
        ShellScriptOutcome shellScriptOutcome = shellScriptHelperService.prepareShellScriptOutcome(
                commandExecutionData.getSweepingOutputEnvVariables(), shellScriptStepParameters.getOutputVariables());
        if (shellScriptOutcome != null) {
          stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                  .name(OutputExpressionConstants.OUTPUT)
                  .outcome(shellScriptOutcome)
                  .build());
        }
      }
      return stepResponseBuilder.build();
    } finally {
    }
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
