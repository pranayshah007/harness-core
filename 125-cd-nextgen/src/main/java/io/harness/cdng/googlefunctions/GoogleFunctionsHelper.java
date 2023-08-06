/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.googlefunctions;
import static io.harness.cdng.manifest.ManifestType.GOOGLE_FUNCTIONS_GEN_ONE_SUPPORTED_MANIFEST_TYPES;
import static io.harness.cdng.manifest.ManifestType.GOOGLE_FUNCTIONS_SUPPORTED_MANIFEST_TYPES;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionGenOnePrepareRollbackOutcome;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionGenOneStepOutcome;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionPrepareRollbackOutcome;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionStepOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.sweepingoutput.GoogleFunctionsServiceCustomSweepingOutput;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.GoogleFunctionToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.gitcommon.GitRequestFileConfig;
import io.harness.delegate.task.gitcommon.GitTaskNGRequest;
import io.harness.delegate.task.gitcommon.GitTaskNGResponse;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionCommandTypeNG;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionGenOnePrepareRollbackRequest;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionGenOnePrepareRollbackRequest.GoogleFunctionGenOnePrepareRollbackRequestBuilder;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionPrepareRollbackRequest;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionPrepareRollbackRequest.GoogleFunctionPrepareRollbackRequestBuilder;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionGenOnePrepareRollbackResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionPrepareRollbackResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.googlefunctions.command.GoogleFunctionsCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class GoogleFunctionsHelper extends CDStepHelper {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private GoogleFunctionsEntityHelper googleFunctionsEntityHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  private final String GOOGLE_FUNCTION_PREPARE_ROLLBACK_COMMAND_NAME = "PrepareRollbackCloudFunction";
  private final String GOOGLE_FUNCTION_GEN_TWO_ENV_TYPE = "GenTwo";
  private final String GOOGLE_FUNCTION_GEN_ONE_ENV_TYPE = "GenOne";
  private final String INVALID_ENV_TYPE = "Environment version is invalid in Google Function Service";

  public TaskChainResponse startChainLink(Ambiance ambiance, StepElementParameters stepElementParameters) {
    // Get ManifestsOutcome
    ManifestsOutcome manifestsOutcome = resolveGoogleFunctionsManifestsOutcome(ambiance);

    // Get InfrastructureOutcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    // Update expressions in ManifestsOutcome
    cdExpressionResolver.updateExpressions(ambiance, manifestsOutcome);

    // Validate ManifestsOutcome
    validateManifestsOutcome(ambiance, manifestsOutcome);

    // fetch environment type
    String environmentType = fetchEnvironmentType(ambiance);

    ManifestOutcome googleFunctionsManifestOutcome =
        getGoogleFunctionsManifestOutcome(manifestsOutcome.values(), environmentType);

    LogCallback logCallback = getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);

    if (isHarnessStoreManifest(googleFunctionsManifestOutcome)) {
      // get Harness Store Manifests Content
      String manifestContent =
          getHarnessStoreManifestFilesContent(ambiance, googleFunctionsManifestOutcome, logCallback);
      GoogleFunctionsStepPassThroughData googleFunctionsPrepareRollbackStepPassThroughData =
          GoogleFunctionsStepPassThroughData.builder()
              .manifestOutcome(googleFunctionsManifestOutcome)
              .manifestContent(manifestContent)
              .infrastructureOutcome(infrastructureOutcome)
              .environmentType(environmentType)
              .build();
      UnitProgressData unitProgressData = getCommandUnitProgressData(
          GoogleFunctionsCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);
      return executePrepareRollbackTask(
          ambiance, stepElementParameters, googleFunctionsPrepareRollbackStepPassThroughData, unitProgressData);
    } else {
      return prepareManifestGitFetchTask(
          infrastructureOutcome, ambiance, stepElementParameters, googleFunctionsManifestOutcome, environmentType);
    }
  }

  private String fetchEnvironmentType(Ambiance ambiance) {
    String environmentType = GOOGLE_FUNCTION_GEN_TWO_ENV_TYPE;
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.GOOGLE_FUNCTION_SERVICE_SWEEPING_OUTPUT));
    if (optionalSweepingOutput.isFound()) {
      GoogleFunctionsServiceCustomSweepingOutput googleFunctionsServiceCustomSweepingOutput =
          (GoogleFunctionsServiceCustomSweepingOutput) optionalSweepingOutput.getOutput();
      if (!GOOGLE_FUNCTION_GEN_ONE_ENV_TYPE.equals(googleFunctionsServiceCustomSweepingOutput.getEnvironmentType())
          && !GOOGLE_FUNCTION_GEN_TWO_ENV_TYPE.equals(
              googleFunctionsServiceCustomSweepingOutput.getEnvironmentType())) {
        throw new InvalidRequestException(INVALID_ENV_TYPE, USER);
      }
      environmentType = googleFunctionsServiceCustomSweepingOutput.getEnvironmentType();
    }
    return environmentType;
  }

  private TaskChainResponse handlePrepareRollbackDataResponse(
      GoogleFunctionPrepareRollbackResponse googleFunctionPrepareRollbackResponse,
      GoogleFunctionsStepExecutor googleFunctionsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters,
      GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData) {
    if (googleFunctionPrepareRollbackResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      GoogleFunctionsStepExceptionPassThroughData googleFunctionsStepExceptionPassThroughData =
          GoogleFunctionsStepExceptionPassThroughData.builder()
              .errorMsg(googleFunctionPrepareRollbackResponse.getErrorMessage())
              .unitProgressData(googleFunctionPrepareRollbackResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder()
          .passThroughData(googleFunctionsStepExceptionPassThroughData)
          .chainEnd(true)
          .build();
    }

    GoogleFunctionPrepareRollbackOutcome googleFunctionPrepareRollbackOutcome =
        GoogleFunctionPrepareRollbackOutcome.builder()
            .cloudFunctionAsString(googleFunctionPrepareRollbackResponse.getCloudFunctionAsString())
            .cloudRunServiceAsString(googleFunctionPrepareRollbackResponse.getCloudRunServiceAsString())
            .isFirstDeployment(googleFunctionPrepareRollbackResponse.isFirstDeployment())
            .manifestContent(googleFunctionsStepPassThroughData.getManifestContent())
            .build();
    executionSweepingOutputService.consume(ambiance,
        OutcomeExpressionConstants.GOOGLE_FUNCTION_PREPARE_ROLLBACK_OUTCOME, googleFunctionPrepareRollbackOutcome,
        StepOutcomeGroup.STEP.name());

    return googleFunctionsStepExecutor.executeTask(ambiance, stepElementParameters, googleFunctionsStepPassThroughData,
        googleFunctionPrepareRollbackResponse.getUnitProgressData());
  }

  private TaskChainResponse handlePrepareRollbackDataResponseGenOne(
      GoogleFunctionGenOnePrepareRollbackResponse googleFunctionGenOnePrepareRollbackResponse,
      GoogleFunctionsStepExecutor googleFunctionsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters,
      GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData) {
    if (googleFunctionGenOnePrepareRollbackResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      GoogleFunctionsStepExceptionPassThroughData googleFunctionsStepExceptionPassThroughData =
          GoogleFunctionsStepExceptionPassThroughData.builder()
              .errorMsg(googleFunctionGenOnePrepareRollbackResponse.getErrorMessage())
              .unitProgressData(googleFunctionGenOnePrepareRollbackResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder()
          .passThroughData(googleFunctionsStepExceptionPassThroughData)
          .chainEnd(true)
          .build();
    }

    GoogleFunctionGenOnePrepareRollbackOutcome googleFunctionGenOnePrepareRollbackOutcome =
        GoogleFunctionGenOnePrepareRollbackOutcome.builder()
            .createFunctionRequestAsString(
                googleFunctionGenOnePrepareRollbackResponse.getCreateFunctionRequestAsString())
            .isFirstDeployment(googleFunctionGenOnePrepareRollbackResponse.isFirstDeployment())
            .manifestContent(googleFunctionsStepPassThroughData.getManifestContent())
            .build();
    executionSweepingOutputService.consume(ambiance,
        OutcomeExpressionConstants.GOOGLE_FUNCTION_GEN_ONE_PREPARE_ROLLBACK_OUTCOME,
        googleFunctionGenOnePrepareRollbackOutcome, StepOutcomeGroup.STEP.name());

    return googleFunctionsStepExecutor.executeTask(ambiance, stepElementParameters, googleFunctionsStepPassThroughData,
        googleFunctionGenOnePrepareRollbackResponse.getUnitProgressData());
  }

  public TaskChainResponse executePrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters,
      GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = googleFunctionsStepPassThroughData.getInfrastructureOutcome();
    if (GOOGLE_FUNCTION_GEN_TWO_ENV_TYPE.equals(googleFunctionsStepPassThroughData.getEnvironmentType())) {
      GoogleFunctionPrepareRollbackRequestBuilder googleFunctionPrepareRollbackRequestBuilder =
          GoogleFunctionPrepareRollbackRequest.builder()
              .googleFunctionCommandType(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_PREPARE_ROLLBACK)
              .commandName(GOOGLE_FUNCTION_PREPARE_ROLLBACK_COMMAND_NAME)
              .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
              .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
              .googleFunctionInfraConfig(getInfraConfig(infrastructureOutcome, ambiance))
              .googleFunctionDeployManifestContent(googleFunctionsStepPassThroughData.getManifestContent());
      return queueTask(stepParameters, googleFunctionPrepareRollbackRequestBuilder.build(), ambiance,
          googleFunctionsStepPassThroughData, false, TaskType.GOOGLE_FUNCTION_PREPARE_ROLLBACK_TASK);
    }
    GoogleFunctionGenOnePrepareRollbackRequestBuilder googleFunctionGenOnePrepareRollbackRequestBuilder =
        GoogleFunctionGenOnePrepareRollbackRequest.builder()
            .googleFunctionCommandType(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_GEN_ONE_PREPARE_ROLLBACK)
            .commandName(GOOGLE_FUNCTION_PREPARE_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .googleFunctionInfraConfig(getInfraConfig(infrastructureOutcome, ambiance))
            .googleFunctionDeployManifestContent(googleFunctionsStepPassThroughData.getManifestContent());
    return queueTask(stepParameters, googleFunctionGenOnePrepareRollbackRequestBuilder.build(), ambiance,
        googleFunctionsStepPassThroughData, false, TaskType.GOOGLE_FUNCTION_GEN_ONE_PREPARE_ROLLBACK_TASK);
  }

  public TaskChainResponse executeNextLink(GoogleFunctionsStepExecutor googleFunctionsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData =
        (GoogleFunctionsStepPassThroughData) passThroughData;
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    try {
      if (responseData instanceof GitTaskNGResponse) {
        GitTaskNGResponse gitTaskResponse = (GitTaskNGResponse) responseData;
        taskChainResponse = handleGitFetchFilesResponse(
            gitTaskResponse, ambiance, stepElementParameters, googleFunctionsStepPassThroughData);
      } else if (responseData instanceof GoogleFunctionPrepareRollbackResponse) {
        GoogleFunctionPrepareRollbackResponse googleFunctionPrepareRollbackResponse =
            (GoogleFunctionPrepareRollbackResponse) responseData;
        taskChainResponse = handlePrepareRollbackDataResponse(googleFunctionPrepareRollbackResponse,
            googleFunctionsStepExecutor, ambiance, stepElementParameters, googleFunctionsStepPassThroughData);
      } else if (responseData instanceof GoogleFunctionGenOnePrepareRollbackResponse) {
        GoogleFunctionGenOnePrepareRollbackResponse googleFunctionGenOnePrepareRollbackResponse =
            (GoogleFunctionGenOnePrepareRollbackResponse) responseData;
        taskChainResponse = handlePrepareRollbackDataResponseGenOne(googleFunctionGenOnePrepareRollbackResponse,
            googleFunctionsStepExecutor, ambiance, stepElementParameters, googleFunctionsStepPassThroughData);
      }
    } catch (Exception e) {
      return TaskChainResponse.builder()
          .chainEnd(true)
          .passThroughData(GoogleFunctionsStepExceptionPassThroughData.builder()
                               .errorMsg(ExceptionUtils.getMessage(e))
                               .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
                               .build())
          .build();
    }
    return taskChainResponse;
  }

  public GoogleFunctionStepOutcome getGoogleFunctionStepOutcome(GoogleFunction function) {
    List<GoogleFunctionStepOutcome.GoogleCloudRunRevision> revisions = newArrayList();

    function.getActiveCloudRunRevisions().forEach(cloudRunRevision -> {
      revisions.add(GoogleFunctionStepOutcome.GoogleCloudRunRevision.builder()
                        .revision(cloudRunRevision.getRevision())
                        .trafficPercent(cloudRunRevision.getTrafficPercent())
                        .build());
    });

    GoogleFunctionStepOutcome.GoogleCloudRunService googleCloudRunService =
        GoogleFunctionStepOutcome.GoogleCloudRunService.builder()
            .serviceName(function.getCloudRunService().getServiceName())
            .memory(function.getCloudRunService().getMemory())
            .revision(function.getCloudRunService().getRevision())
            .build();

    return GoogleFunctionStepOutcome.builder()
        .functionName(function.getFunctionName())
        .cloudRunService(googleCloudRunService)
        .activeCloudRunRevisions(revisions)
        .runtime(function.getRuntime())
        .environment(function.getEnvironment())
        .state(function.getState())
        .url(function.getUrl())
        .source(function.getSource())
        .build();
  }

  public GoogleFunctionGenOneStepOutcome getGoogleFunctionGenOneStepOutcome(GoogleFunction function) {
    return GoogleFunctionGenOneStepOutcome.builder()
        .functionName(function.getFunctionName())
        .runtime(function.getRuntime())
        .environment(function.getEnvironment())
        .state(function.getState())
        .url(function.getUrl())
        .source(function.getSource())
        .build();
  }

  public StepResponse generateStepResponse(GoogleFunctionCommandResponse googleFunctionCommandResponse,
      StepResponseBuilder stepResponseBuilder, Ambiance ambiance) {
    if (googleFunctionCommandResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return getFailureResponseBuilder(googleFunctionCommandResponse, stepResponseBuilder).build();
    } else {
      InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
          ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig =
          (GcpGoogleFunctionInfraConfig) getInfraConfig(infrastructureOutcome, ambiance);
      List<ServerInstanceInfo> serverInstanceInfoList = getServerInstanceInfo(
          googleFunctionCommandResponse, gcpGoogleFunctionInfraConfig, infrastructureOutcome.getInfrastructureKey());
      instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
      GoogleFunctionStepOutcome googleFunctionStepOutcome =
          getGoogleFunctionStepOutcome(googleFunctionCommandResponse.getFunction());

      return stepResponseBuilder.status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.OUTPUT)
                           .outcome(googleFunctionStepOutcome)
                           .build())
          .build();
    }
  }

  public StepResponse generateGenOneStepResponse(GoogleFunctionCommandResponse googleFunctionCommandResponse,
      StepResponseBuilder stepResponseBuilder, Ambiance ambiance) {
    if (googleFunctionCommandResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return getFailureResponseBuilder(googleFunctionCommandResponse, stepResponseBuilder).build();
    } else {
      InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
          ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig =
          (GcpGoogleFunctionInfraConfig) getInfraConfig(infrastructureOutcome, ambiance);
      List<ServerInstanceInfo> serverInstanceInfoList = getGenOneServerInstanceInfo(
          googleFunctionCommandResponse, gcpGoogleFunctionInfraConfig, infrastructureOutcome.getInfrastructureKey());
      instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
      GoogleFunctionGenOneStepOutcome googleFunctionGenOneStepOutcome =
          getGoogleFunctionGenOneStepOutcome(googleFunctionCommandResponse.getFunction());

      return stepResponseBuilder.status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.OUTPUT)
                           .outcome(googleFunctionGenOneStepOutcome)
                           .build())
          .build();
    }
  }

  public StepResponse handleTaskException(
      Ambiance ambiance, GoogleFunctionsStepPassThroughData stepPassThroughData, Exception e) throws Exception {
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }

    UnitProgressData unitProgressData =
        completeUnitProgressData(stepPassThroughData.getLastActiveUnitProgressData(), ambiance, e.getMessage());
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(io.harness.eraro.Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(HarnessStringUtils.emptyIfNull(ExceptionUtils.getMessage(e)))
                                  .build();

    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .addAllFailureTypes(failureData.getFailureTypesList())
                         .setErrorMessage(failureData.getMessage())
                         .addFailureData(failureData)
                         .build())
        .build();
  }

  public static StepResponseBuilder getFailureResponseBuilder(
      GoogleFunctionCommandResponse googleFunctionCommandResponse, StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .setErrorMessage(googleFunctionCommandResponse.getErrorMessage() == null
                                 ? ""
                                 : googleFunctionCommandResponse.getErrorMessage())
                         .build());
    return stepResponseBuilder;
  }

  private TaskChainResponse handleGitFetchFilesResponse(GitTaskNGResponse gitTaskResponse, Ambiance ambiance,
      StepElementParameters stepElementParameters,
      GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData) {
    if (gitTaskResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      GoogleFunctionsStepExceptionPassThroughData googleFunctionsStepExceptionPassThroughData =
          GoogleFunctionsStepExceptionPassThroughData.builder()
              .errorMsg(gitTaskResponse.getErrorMessage())
              .unitProgressData(gitTaskResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder()
          .passThroughData(googleFunctionsStepExceptionPassThroughData)
          .chainEnd(true)
          .build();
    }
    String manifestContent = getManifestContentFromGitResponse(gitTaskResponse, ambiance);
    GoogleFunctionsStepPassThroughData googleFunctionsPrepareRollbackStepPassThroughData =
        GoogleFunctionsStepPassThroughData.builder()
            .manifestOutcome(googleFunctionsStepPassThroughData.getManifestOutcome())
            .manifestContent(manifestContent)
            .infrastructureOutcome(googleFunctionsStepPassThroughData.getInfrastructureOutcome())
            .environmentType(googleFunctionsStepPassThroughData.getEnvironmentType())
            .build();

    return executePrepareRollbackTask(ambiance, stepElementParameters,
        googleFunctionsPrepareRollbackStepPassThroughData, gitTaskResponse.getUnitProgressData());
  }

  private String getManifestContentFromGitResponse(GitTaskNGResponse gitTaskResponse, Ambiance ambiance) {
    String manifestContent = gitTaskResponse.getGitFetchFilesResults().get(0).getFiles().get(0).getFileContent();
    return cdExpressionResolver.renderExpression(ambiance, manifestContent);
  }

  private TaskChainResponse prepareManifestGitFetchTask(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance,
      StepElementParameters stepElementParameters, ManifestOutcome manifestOutcome, String environmentType) {
    GitRequestFileConfig gitRequestFileConfig = null;

    if (ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind())) {
      gitRequestFileConfig = getGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance);
    }

    GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData =
        GoogleFunctionsStepPassThroughData.builder()
            .manifestOutcome(manifestOutcome)
            .infrastructureOutcome(infrastructureOutcome)
            .environmentType(environmentType)
            .build();

    return getGitFetchFileTaskResponse(
        ambiance, false, stepElementParameters, googleFunctionsStepPassThroughData, gitRequestFileConfig);
  }

  private TaskChainResponse getGitFetchFileTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters,
      GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData,
      GitRequestFileConfig gitRequestFileConfig) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    GitTaskNGRequest gitTaskNGRequest =
        GitTaskNGRequest.builder()
            .accountId(accountId)
            .gitRequestFileConfigs(Collections.singletonList(gitRequestFileConfig))
            .shouldOpenLogStream(shouldOpenLogStream)
            .commandUnitName(GoogleFunctionsCommandUnitConstants.fetchManifests.toString())
            .closeLogStream(true)
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.GIT_TASK_NG.name())
                                  .parameters(new Object[] {gitTaskNGRequest})
                                  .build();

    String taskName = TaskType.GIT_TASK_NG.getDisplayName();

    GoogleFunctionsSpecParameters googleFunctionsSpecParameters =
        (GoogleFunctionsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, googleFunctionsSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(googleFunctionsSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(googleFunctionsStepPassThroughData)
        .build();
  }

  public StepResponse handleStepExceptionFailure(GoogleFunctionsStepExceptionPassThroughData stepException) {
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(io.harness.eraro.Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(HarnessStringUtils.emptyIfNull(stepException.getErrorMsg()))
                                  .build();
    return StepResponse.builder()
        .unitProgressList(stepException.getUnitProgressData().getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .addAllFailureTypes(failureData.getFailureTypesList())
                         .setErrorMessage(failureData.getMessage())
                         .addFailureData(failureData)
                         .build())
        .build();
  }

  private GitRequestFileConfig getGitFetchFilesConfigFromManifestOutcome(
      ManifestOutcome manifestOutcome, Ambiance ambiance) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Ecs step", USER);
    }
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    return getGitFetchFilesConfig(ambiance, gitStoreConfig, manifestOutcome);
  }

  private GitRequestFileConfig getGitFetchFilesConfig(
      Ambiance ambiance, GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome) {
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    String validationMessage = format("Google function manifest with Id [%s]", manifestOutcome.getIdentifier());
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorInfoDTO connectorDTO = googleFunctionsEntityHelper.getConnectorInfoDTO(connectorId, ngAccess);
    validateManifest(gitStoreConfig.getKind(), connectorDTO, validationMessage);
    return GitRequestFileConfig.builder()
        .gitStoreDelegateConfig(getGitStoreDelegateConfig(
            gitStoreConfig, connectorDTO, manifestOutcome, gitStoreConfig.getPaths().getValue(), ambiance))
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .succeedIfFileNotFound(false)
        .build();
  }

  private String getHarnessStoreManifestFilesContent(
      Ambiance ambiance, ManifestOutcome manifestOutcome, LogCallback logCallback) {
    // Harness Store manifest
    String harnessStoreManifestContent = null;
    if (ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind())) {
      harnessStoreManifestContent = fetchFilesContentFromLocalStore(ambiance, manifestOutcome, logCallback).get(0);
    }
    // Render expressions for all file content fetched from Harness File Store
    if (harnessStoreManifestContent != null) {
      harnessStoreManifestContent = cdExpressionResolver.renderExpression(ambiance, harnessStoreManifestContent);
    }
    return harnessStoreManifestContent;
  }

  public TaskChainResponse queueTask(StepElementParameters stepElementParameters,
      GoogleFunctionCommandRequest googleFunctionCommandRequest, Ambiance ambiance, PassThroughData passThroughData,
      boolean isChainEnd, TaskType taskType) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {googleFunctionCommandRequest})
                            .taskType(taskType.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();
    String taskName = taskType.getDisplayName() + " : " + googleFunctionCommandRequest.getCommandName();
    GoogleFunctionsSpecParameters googleFunctionsSpecParameters =
        (GoogleFunctionsSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, googleFunctionsSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(googleFunctionsSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public ArtifactOutcome getArtifactOutcome(Ambiance ambiance) {
    OptionalOutcome artifactsOutcomeOption = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (artifactsOutcomeOption.isFound()) {
      ArtifactsOutcome artifactsOutcome = (ArtifactsOutcome) artifactsOutcomeOption.getOutcome();
      if (artifactsOutcome.getPrimary() != null) {
        return artifactsOutcome.getPrimary();
      }
    }
    throw new InvalidRequestException("Google Cloud Function Artifact is mandatory.", USER);
  }

  public ManifestOutcome getGoogleFunctionsManifestOutcome(
      @NotEmpty Collection<ManifestOutcome> manifestOutcomes, String environmentType) {
    // Filter only  Google Cloud Functions supported manifest types
    Set<String> supportedManifestTypes = GOOGLE_FUNCTION_GEN_TWO_ENV_TYPE.equals(environmentType)
        ? GOOGLE_FUNCTIONS_SUPPORTED_MANIFEST_TYPES
        : GOOGLE_FUNCTIONS_GEN_ONE_SUPPORTED_MANIFEST_TYPES;

    List<ManifestOutcome> googleFunctionsManifests =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> supportedManifestTypes.contains(manifestOutcome.getType()))
            .collect(Collectors.toList());

    // Check if Google Cloud Functions Manifests are empty
    if (isEmpty(googleFunctionsManifests)) {
      throw new InvalidRequestException("Google Cloud Function Manifest is mandatory.", USER);
    }
    return googleFunctionsManifests.get(0);
  }

  public ManifestsOutcome resolveGoogleFunctionsManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType = Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance))
                            .map(StepType::getType)
                            .orElse("Google Function");
      throw new GeneralException(
          format("No manifests found in stage %s. %s step requires a manifest defined in stage service definition",
              stageName, stepType));
    }
    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  public GoogleFunctionInfraConfig getInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return googleFunctionsEntityHelper.getInfraConfig(infrastructure, ngAccess);
  }

  public boolean isHarnessStoreManifest(ManifestOutcome manifestOutcome) {
    return manifestOutcome.getStore() != null && ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind());
  }

  public List<ServerInstanceInfo> getServerInstanceInfo(GoogleFunctionCommandResponse googleFunctionCommandResponse,
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig, String infrastructureKey) {
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();
    GoogleFunction googleFunction = googleFunctionCommandResponse.getFunction();
    if (googleFunction != null && googleFunction.getCloudRunService() != null) {
      serverInstanceInfoList.add(GoogleFunctionToServerInstanceInfoMapper.toServerInstanceInfo(googleFunction,
          googleFunction.getCloudRunService().getRevision(), gcpGoogleFunctionInfraConfig.getProject(),
          gcpGoogleFunctionInfraConfig.getRegion(), infrastructureKey));
    }
    return serverInstanceInfoList;
  }

  public List<ServerInstanceInfo> getGenOneServerInstanceInfo(
      GoogleFunctionCommandResponse googleFunctionCommandResponse,
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig, String infrastructureKey) {
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();
    GoogleFunction googleFunction = googleFunctionCommandResponse.getFunction();
    if (googleFunction != null) {
      serverInstanceInfoList.add(GoogleFunctionToServerInstanceInfoMapper.toGenOneServerInstanceInfo(googleFunction,
          gcpGoogleFunctionInfraConfig.getProject(), gcpGoogleFunctionInfraConfig.getRegion(), infrastructureKey));
    }
    return serverInstanceInfoList;
  }
}
