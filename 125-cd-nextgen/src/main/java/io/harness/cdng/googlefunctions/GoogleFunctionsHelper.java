package io.harness.cdng.googlefunctions;

import com.google.inject.Inject;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.serverless.ServerlessSpecParameters;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.gitcommon.GitRequestFileConfig;
import io.harness.delegate.task.gitcommon.GitTaskNGRequest;
import io.harness.delegate.task.gitcommon.GitTaskNGResponse;
import io.harness.delegate.task.googlefunctions.GoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionCommandRequest;
import io.harness.delegate.task.googlefunctions.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.googlefunctions.GoogleFunctionsCommandUnitConstants;
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
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import software.wings.beans.TaskType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;
import static java.lang.String.format;

@Slf4j
public class GoogleFunctionsHelper extends CDStepHelper {
    @Inject private EngineExpressionService engineExpressionService;
    @Inject private GoogleFunctionsEntityHelper googleFunctionsEntityHelper;


    public TaskChainResponse startChainLink(GoogleFunctionsStepExecutor googleFunctionsStepExecutor, Ambiance ambiance,
                                            StepElementParameters stepElementParameters) {
        // Get ManifestsOutcome
        ManifestsOutcome manifestsOutcome = resolveGoogleFunctionsManifestsOutcome(ambiance);

        // Get InfrastructureOutcome
        InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
                ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

        // Update expressions in ManifestsOutcome
        ExpressionEvaluatorUtils.updateExpressions(
                manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

        // Validate ManifestsOutcome
        validateManifestsOutcome(ambiance, manifestsOutcome);

        ManifestOutcome googleFunctionsManifestOutcome = getGoogleFunctionsManifestOutcome(manifestsOutcome.values());

        LogCallback logCallback = getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);

        if(isHarnessStoreManifest(googleFunctionsManifestOutcome)) {
            // get Harness Store Manifests Content
            String manifestContent =
                    getHarnessStoreManifestFilesContent(ambiance, googleFunctionsManifestOutcome, logCallback);
            GoogleFunctionsStepPassThroughData googleFunctionsPrepareRollbackStepPassThroughData =
                    GoogleFunctionsStepPassThroughData.builder()
                            .manifestOutcome(googleFunctionsManifestOutcome)
                            .manifestContent(manifestContent)
                            .infrastructureOutcome(infrastructureOutcome)
                            .build();
            UnitProgressData unitProgressData =
                    getCommandUnitProgressData(GoogleFunctionsCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);
            return googleFunctionsStepExecutor.executePrepareRollbackTask(ambiance,
                    stepElementParameters, googleFunctionsPrepareRollbackStepPassThroughData, unitProgressData);
        }
        else{
            return prepareManifestGitFetchTask(infrastructureOutcome, ambiance, stepElementParameters, googleFunctionsManifestOutcome);
        }


    }

    public TaskChainResponse executeNextLink(GoogleFunctionsStepExecutor googleFunctionsStepExecutor, Ambiance ambiance,
                                             StepElementParameters stepElementParameters, PassThroughData passThroughData,
                                             ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
        ResponseData responseData = responseDataSupplier.get();
        GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData =
                (GoogleFunctionsStepPassThroughData) passThroughData;
        UnitProgressData unitProgressData = null;
        try {
            if (responseData instanceof GitTaskNGResponse) {
                GitTaskNGResponse gitTaskResponse = (GitTaskNGResponse) responseData;
                return handleGitFetchFilesResponse(gitTaskResponse, googleFunctionsStepExecutor, ambiance,
                        stepElementParameters, googleFunctionsStepPassThroughData);
            }
            else {
                return null;
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

    public static StepResponse.StepResponseBuilder getFailureResponseBuilder(
            GoogleFunctionCommandResponse googleFunctionCommandResponse, StepResponse.StepResponseBuilder stepResponseBuilder) {
        stepResponseBuilder.status(Status.FAILED)
                .failureInfo(FailureInfo.newBuilder()
                        .setErrorMessage(googleFunctionCommandResponse.getErrorMessage() == null ? "" :
                                googleFunctionCommandResponse.getErrorMessage())
                        .build());
        return stepResponseBuilder;
    }

    private TaskChainResponse handleGitFetchFilesResponse(GitTaskNGResponse gitTaskResponse,
                                                                    GoogleFunctionsStepExecutor googleFunctionsStepExecutor,
                                                          Ambiance ambiance, StepElementParameters stepElementParameters,
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
                        .build();

        return googleFunctionsStepExecutor.executeDeployTask(ambiance,
                stepElementParameters, googleFunctionsPrepareRollbackStepPassThroughData, gitTaskResponse.getUnitProgressData());
    }

    private String getManifestContentFromGitResponse(GitTaskNGResponse gitTaskResponse, Ambiance ambiance) {
        String manifestContent = gitTaskResponse.getGitFetchFilesResults().get(0).getFiles().get(0).getFileContent();
        return engineExpressionService.renderExpression(ambiance, manifestContent);
    }

    private TaskChainResponse prepareManifestGitFetchTask(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance,
                                                             StepElementParameters stepElementParameters,
                                                             ManifestOutcome manifestOutcome) {

        GitRequestFileConfig gitRequestFileConfig = null;

        if (ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind())) {
            gitRequestFileConfig =
                    getGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance);
        }

        GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData =
                GoogleFunctionsStepPassThroughData.builder()
                        .manifestOutcome(manifestOutcome)
                        .infrastructureOutcome(infrastructureOutcome)
                        .build();

        return getGitFetchFileTaskResponse(ambiance, false, stepElementParameters,
                googleFunctionsStepPassThroughData, gitRequestFileConfig);
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
                        .build();

        final TaskData taskData = TaskData.builder()
                .async(true)
                .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                .taskType(TaskType.GIT_TASK_NG.name())
                .parameters(new Object[] {gitTaskNGRequest})
                .build();

        String taskName = TaskType.GIT_TASK_NG.getDisplayName();

        GoogleFunctionsSpecParameters googleFunctionsSpecParameters = (GoogleFunctionsSpecParameters) stepElementParameters.getSpec();

        final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
                googleFunctionsSpecParameters.getCommandUnits(), taskName,
                TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(googleFunctionsSpecParameters.getDelegateSelectors()))),
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

    private String getHarnessStoreManifestFilesContent(Ambiance ambiance, ManifestOutcome manifestOutcome, LogCallback logCallback) {
        // Harness Store manifest
        String harnessStoreManifestContent = null;
        if (ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind())) {
            harnessStoreManifestContent =
                    fetchFilesContentFromLocalStore(ambiance, manifestOutcome, logCallback).get(0);
        }
        // Render expressions for all file content fetched from Harness File Store

        if (harnessStoreManifestContent != null) {
            harnessStoreManifestContent =
                    engineExpressionService.renderExpression(ambiance, harnessStoreManifestContent);
        }
        return harnessStoreManifestContent;
    }

    public TaskChainResponse queueTask(StepElementParameters stepElementParameters, GoogleFunctionCommandRequest
            googleFunctionCommandRequest, Ambiance ambiance, PassThroughData passThroughData, boolean isChainEnd) {
        TaskData taskData = TaskData.builder()
                .parameters(new Object[] {googleFunctionCommandRequest})
                .taskType(TaskType.GOOGLE_FUNCTION_COMMAND_TASK.name())
                .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                .async(true)
                .build();
        String taskName =
                TaskType.GOOGLE_FUNCTION_COMMAND_TASK.getDisplayName() + " : " + googleFunctionCommandRequest.getCommandName();
        GoogleFunctionsSpecParameters googleFunctionsSpecParameters = (GoogleFunctionsSpecParameters) stepElementParameters.getSpec();
        final TaskRequest taskRequest =
                prepareCDTaskRequest(ambiance, taskData, kryoSerializer, googleFunctionsSpecParameters.getCommandUnits(), taskName,
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
            if(artifactsOutcome.getPrimary()!=null) {
                return artifactsOutcome.getPrimary();
            }
        }
        throw new InvalidRequestException("Google Cloud Function Artifact is mandatory.", USER);
    }

    public ManifestOutcome getGoogleFunctionsManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
        // Filter only  Google Cloud Functions supported manifest types
        List<ManifestOutcome> googleFunctionsManifests =
                manifestOutcomes.stream()
                        .filter(manifestOutcome -> ManifestType.GOOGLE_FUNCTIONS_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
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
            String stepType =
                    Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance))
                            .map(StepType::getType).orElse("Google Function");
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

}
