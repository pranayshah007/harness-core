package io.harness.cdng.ecs;

import com.google.inject.Inject;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessGitFetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ecs.EcsRollingDeployResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.EcsTaskToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.ecs.EcsGitFetchFileConfig;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsGitFetchRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.git.model.FetchFilesResult;
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
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import org.hibernate.validator.constraints.NotEmpty;
import software.wings.beans.TaskType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;
import static java.lang.String.format;

public class EcsStepCommonHelper extends EcsStepUtils {
  @Inject
  private EngineExpressionService engineExpressionService;
  @Inject private EcsEntityHelper ecsEntityHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;

  public TaskChainResponse startChainLink(
          Ambiance ambiance, StepElementParameters stepElementParameters, EcsStepHelper ecsStepHelper) {
    ManifestsOutcome manifestsOutcome = resolveEcsManifestsOutcome(ambiance);
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ExpressionEvaluatorUtils.updateExpressions(
            manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));
    validateManifestsOutcome(ambiance, manifestsOutcome);
    List<ManifestOutcome> ecsManifestOutcome =
            getEcsManifestOutcome(manifestsOutcome.values(), ecsStepHelper);
    return prepareEcsManifestGitFetchTask(
            ambiance, stepElementParameters, infrastructureOutcome, ecsManifestOutcome, ecsStepHelper);
  }

  public List<ManifestOutcome> getEcsManifestOutcome(
          @NotEmpty Collection<ManifestOutcome> manifestOutcomes, EcsStepHelper ecsStepHelper) {
    return ecsStepHelper.getEcsManifestOutcome(manifestOutcomes);
  }

  public ManifestsOutcome resolveEcsManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName =
              AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType =
              Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("Ecs");
      throw new GeneralException(
              format("No manifests found in stage %s. %s step requires a manifest defined in stage service definition",
                      stageName, stepType));
    }
    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  private TaskChainResponse prepareEcsManifestGitFetchTask(Ambiance ambiance,
                                                           StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome,
                                                           List<ManifestOutcome> ecsManifestOutcomes, EcsStepHelper ecsStepHelper) {

    ManifestOutcome ecsTaskDefinitionManifestOutcome =   ecsStepHelper.getEcsTaskDefinitionManifestOutcome(ecsManifestOutcomes);
    EcsGitFetchFileConfig ecsTaskDefinitionGitFetchFileConfig = getEcsGitFetchFilesConfigFromManifestOutcome(ecsTaskDefinitionManifestOutcome, ambiance, ecsStepHelper);

    ManifestOutcome ecsServiceDefinitionManifestOutcome =   ecsStepHelper.getEcsServiceDefinitionManifestOutcome(ecsManifestOutcomes);
    EcsGitFetchFileConfig ecsServiceDefinitionGitFetchFileConfig = getEcsGitFetchFilesConfigFromManifestOutcome(ecsServiceDefinitionManifestOutcome, ambiance, ecsStepHelper);

    List<EcsGitFetchFileConfig> ecsScalableTargetGitFetchFileConfigs = ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalableTargetDefinition).stream()
            .map(manifestOutcome -> getEcsGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance, ecsStepHelper)).collect(Collectors.toList());

    List<EcsGitFetchFileConfig> ecsScalingPolicyGitFetchFileConfigs = ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalingPolicyDefinition).stream()
            .map(manifestOutcome -> getEcsGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance, ecsStepHelper)).collect(Collectors.toList());

    EcsStepPassThroughData ecsStepPassThroughData = EcsStepPassThroughData.builder()
            .ecsManifestOutcomes(ecsManifestOutcomes)
            .infrastructureOutcome(infrastructureOutcome)
            .build();

    return getGitFetchFileTaskResponse(
            ambiance, true, stepElementParameters, ecsStepPassThroughData, ecsTaskDefinitionGitFetchFileConfig,
            ecsServiceDefinitionGitFetchFileConfig, ecsScalableTargetGitFetchFileConfigs, ecsScalingPolicyGitFetchFileConfigs);
  }

  private EcsGitFetchFileConfig getEcsGitFetchFilesConfigFromManifestOutcome(ManifestOutcome manifestOutcome, Ambiance ambiance, EcsStepHelper ecsStepHelper) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Serverless step", USER);
    }
    EcsGitFetchFileConfig ecsGitFetchFileConfig =
            getEcsGitFetchFilesConfig(ambiance, gitStoreConfig, manifestOutcome, ecsStepHelper);
    return ecsGitFetchFileConfig;
  }

  private EcsGitFetchFileConfig getEcsGitFetchFilesConfig(Ambiance ambiance, GitStoreConfig gitStoreConfig,
                                                              ManifestOutcome manifestOutcome, EcsStepHelper serverlessStepHelper) {
    return EcsGitFetchFileConfig.builder()
            .gitStoreDelegateConfig(getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome))
            .identifier(manifestOutcome.getIdentifier())
            .manifestType(manifestOutcome.getType())
            .succeedIfFileNotFound(false)
            .build();
  }

  private TaskChainResponse getGitFetchFileTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
                                                        StepElementParameters stepElementParameters, EcsStepPassThroughData ecsStepPassThroughData,
                                                        EcsGitFetchFileConfig ecsTaskDefinitionGitFetchFileConfig,
                                                        EcsGitFetchFileConfig ecsServiceDefinitionGitFetchFileConfig,
                                                        List<EcsGitFetchFileConfig> ecsScalableTargetGitFetchFileConfigs,
                                                        List<EcsGitFetchFileConfig> ecsScalingPolicyGitFetchFileConfigs) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsGitFetchRequest ecsGitFetchRequest =
            EcsGitFetchRequest.builder()
                    .accountId(accountId)
                    .ecsTaskDefinitionGitFetchFileConfig(ecsTaskDefinitionGitFetchFileConfig)
                    .ecsServiceDefinitionGitFetchFileConfig(ecsServiceDefinitionGitFetchFileConfig)
                    .ecsScalableTargetGitFetchFileConfigs(ecsScalableTargetGitFetchFileConfigs)
                    .ecsScalingPolicyGitFetchFileConfigs(ecsScalingPolicyGitFetchFileConfigs)
                    .shouldOpenLogStream(shouldOpenLogStream)
                    .build();

    final TaskData taskData = TaskData.builder()
            .async(true)
            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
            .taskType(TaskType.ECS_GIT_FETCH_TASK_NG.name())
            .parameters(new Object[] {ecsGitFetchRequest})
            .build();

    String taskName = TaskType.ECS_GIT_FETCH_TASK_NG.getDisplayName();

    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest =
            prepareCDTaskRequest(ambiance, taskData, kryoSerializer, ecsSpecParameters.getCommandUnits(), taskName,
                    TaskSelectorYaml.toTaskSelector(
                            emptyIfNull(getParameterFieldValue(ecsSpecParameters.getDelegateSelectors()))),
                    stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
            .chainEnd(false)
            .taskRequest(taskRequest)
            .passThroughData(ecsStepPassThroughData)
            .build();
  }


  public TaskChainResponse executeNextLink(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
                                           StepElementParameters stepElementParameters, PassThroughData passThroughData,
                                           ThrowingSupplier<ResponseData> responseDataSupplier, EcsStepHelper ecsStepHelper) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    EcsStepPassThroughData ecsStepPassThroughData = (EcsStepPassThroughData) passThroughData;
    UnitProgressData unitProgressData = null;
    try {
      if (responseData instanceof EcsGitFetchResponse) {
        EcsGitFetchResponse serverlessGitFetchResponse = (EcsGitFetchResponse) responseData;
        return handleEcsGitFetchFilesResponse(serverlessGitFetchResponse, ecsStepExecutor, ambiance,
                stepElementParameters, ecsStepPassThroughData, ecsStepHelper);
      }
    } catch (Exception e) {
      return TaskChainResponse.builder()
              .chainEnd(true)
              .passThroughData(ServerlessStepExceptionPassThroughData.builder()
                      .errorMessage(ExceptionUtils.getMessage(e))
                      .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
                      .build())
              .build();
    }
    return null;
  }

  public EcsInfraConfig getEcsInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return ecsEntityHelper.getEcsInfraConfig(infrastructure, ngAccess);
  }

  private TaskChainResponse handleEcsGitFetchFilesResponse(EcsGitFetchResponse ecsGitFetchResponse,
                                                                  EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
                                                                  EcsStepPassThroughData ecsStepPassThroughData, EcsStepHelper ecsStepHelper) {
    if (ecsGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData =
              EcsGitFetchFailurePassThroughData.builder()
                      .errorMsg(ecsGitFetchResponse.getErrorMessage())
                      .unitProgressData(ecsGitFetchResponse.getUnitProgressData())
                      .build();
      return TaskChainResponse.builder()
              .passThroughData(ecsGitFetchFailurePassThroughData)
              .chainEnd(true)
              .build();
    }

    EcsExecutionPassThroughData ecsExecutionPassThroughData = EcsExecutionPassThroughData.builder()
            .infrastructure(ecsStepPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsGitFetchResponse.getUnitProgressData())
            .build();

    FetchFilesResult ecsTaskDefinitionFetchFileResult = ecsGitFetchResponse.getEcsTaskDefinitionFetchFilesResult();
    FetchFilesResult ecsServiceDefinitionFetchFileResult = ecsGitFetchResponse.getEcsServiceDefinitionFetchFilesResult();


    String ecsTaskDefinitionFileContent = ecsTaskDefinitionFetchFileResult.getFiles().get(0).getFileContent();
    String ecsServiceDefinitionFileContent = ecsServiceDefinitionFetchFileResult.getFiles().get(0).getFileContent();

    ecsTaskDefinitionFileContent = engineExpressionService.renderExpression(ambiance, ecsTaskDefinitionFileContent);
    ecsServiceDefinitionFileContent = engineExpressionService.renderExpression(ambiance, ecsServiceDefinitionFileContent);

    EcsStepExecutorParams ecsStepExecutorParams = EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
            .build();

    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData, ecsGitFetchResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  public TaskChainResponse queueEcsTask(StepElementParameters stepElementParameters,
                                        EcsCommandRequest ecsCommandRequest, Ambiance ambiance, PassThroughData passThroughData,
                                        boolean isChainEnd) {
    TaskData taskData = TaskData.builder()
            .parameters(new Object[] {ecsCommandRequest})
            .taskType(TaskType.ECS_COMMAND_TASK_NG.name())
            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
            .async(true)
            .build();

    String taskName =
            TaskType.ECS_COMMAND_TASK_NG.getDisplayName() + " : " + ecsCommandRequest.getCommandName();

    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest =
            prepareCDTaskRequest(ambiance, taskData, kryoSerializer, ecsSpecParameters.getCommandUnits(), taskName,
                    TaskSelectorYaml.toTaskSelector(
                            emptyIfNull(getParameterFieldValue(ecsSpecParameters.getDelegateSelectors()))),
                    stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
            .taskRequest(taskRequest)
            .chainEnd(isChainEnd)
            .passThroughData(passThroughData)
            .build();
  }

  public StepResponse handleGitTaskFailure(EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData) {
    UnitProgressData unitProgressData = ecsGitFetchFailurePassThroughData.getUnitProgressData();
    return StepResponse.builder()
            .unitProgressList(unitProgressData.getUnitProgresses())
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().setErrorMessage(ecsGitFetchFailurePassThroughData.getErrorMsg()).build())
            .build();
  }

  public StepResponse handleStepExceptionFailure(EcsStepExceptionPassThroughData stepException) {
    FailureData failureData = FailureData.newBuilder()
            .addFailureTypes(FailureType.APPLICATION_FAILURE)
            .setLevel(io.harness.eraro.Level.ERROR.name())
            .setCode(GENERAL_ERROR.name())
            .setMessage(HarnessStringUtils.emptyIfNull(stepException.getErrorMessage()))
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

  public StepResponse handleTaskException(
          Ambiance ambiance, EcsExecutionPassThroughData executionPassThroughData, Exception e) throws Exception {
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }

    UnitProgressData unitProgressData =
            completeUnitProgressData(executionPassThroughData.getLastActiveUnitProgressData(), ambiance, e.getMessage());
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

  public static StepResponse.StepResponseBuilder getFailureResponseBuilder(
          EcsCommandResponse serverlessCommandResponse, StepResponse.StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder()
                    .setErrorMessage(EcsStepCommonHelper.getErrorMessage(serverlessCommandResponse))
                    .build());
    return stepResponseBuilder;
  }

  public static String getErrorMessage(EcsCommandResponse ecsCommandResponse) {
    return ecsCommandResponse.getErrorMessage() == null ? "" : ecsCommandResponse.getErrorMessage();
  }

  public List<ServerInstanceInfo> getServerInstanceInfos(EcsCommandResponse ecsCommandResponse, String infrastructureKey) {
    if(ecsCommandResponse instanceof EcsRollingDeployResponse) {
      EcsRollingDeployResult ecsRollingDeployResult = (EcsRollingDeployResult)
              ((EcsRollingDeployResponse) ecsCommandResponse).getEcsDeployResult();
      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(ecsRollingDeployResult.getEcsTasks(), infrastructureKey,
              ecsRollingDeployResult.getRegion());
    }
    throw new GeneralException("Invalid ecs command response instance");
  }

}
