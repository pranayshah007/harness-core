package io.harness.cdng.googlefunctions;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessGitFetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessS3FetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.ServerlessNGException;
import io.harness.delegate.task.googlefunctions.GoogleFunctionCommandTypeNG;
import io.harness.delegate.task.googlefunctions.request.GoogleFunctionDeployRequest;
import static io.harness.delegate.task.googlefunctions.request.GoogleFunctionDeployRequest.GoogleFunctionDeployRequestBuilder;

import io.harness.delegate.task.googlefunctions.response.GoogleFunctionDeployResponse;
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
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class GoogleFunctionsDeployStep extends TaskChainExecutableWithRollbackAndRbac implements GoogleFunctionsStepExecutor {
    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(ExecutionNodeType.GOOGLE_CLOUD_FUNCTIONS_DEPLOY.getYamlType())
            .setStepCategory(StepCategory.STEP)
            .build();

    private final String GOOGLE_FUNCTION_DEPLOY_COMMAND_NAME = "DeployCloudFunction";
    private final String GOOGLE_FUNCTION_PREPARE_ROLLBACK_COMMAND_NAME = "PrepareRollbackCloudFunction";


    @Inject private InstanceInfoService instanceInfoService;
    @Inject private GoogleFunctionsHelper googleFunctionsHelper;
    @Inject private GoogleFunctionsEntityHelper googleFunctionsEntityHelper;

    @Override
    public TaskChainResponse executeDeployTask(Ambiance ambiance, StepElementParameters stepParameters,
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
                                googleFunctionsHelper.getArtifactOutcome(ambiance),  AmbianceUtils.getNgAccess(ambiance)));

        if (googleFunctionsDeployStepParameters.getUpdateFieldMask().getValue() != null) {
            googleFunctionDeployRequestBuilder.updateFieldMaskContent(
                    googleFunctionsDeployStepParameters.getUpdateFieldMask().getValue());
        }
        return googleFunctionsHelper.queueTask(stepParameters, googleFunctionDeployRequestBuilder.build(), ambiance,
                googleFunctionsStepPassThroughData, true);
    }

    @Override
    public TaskChainResponse executePrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters,
                                                        GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData,
                                                        UnitProgressData unitProgressData) {
        return null;
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
                                                                StepInputPackage inputPackage, PassThroughData passThroughData,
                                                                ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
        log.info("Calling executeNextLink");
        return googleFunctionsHelper.executeNextLink(this, ambiance, stepParameters, passThroughData,
                responseSupplier);
    }

    @Override
    public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
                                                             PassThroughData passThroughData,
                                                             ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
        if (passThroughData instanceof GoogleFunctionsStepExceptionPassThroughData) {
            return googleFunctionsHelper.handleStepExceptionFailure(
                    (GoogleFunctionsStepExceptionPassThroughData) passThroughData);
        }

        log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
        GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData =
                (GoogleFunctionsStepPassThroughData) passThroughData;
        InfrastructureOutcome infrastructureOutcome = googleFunctionsStepPassThroughData.getInfrastructureOutcome();
        GoogleFunctionDeployResponse googleFunctionDeployResponse;
        try {
            googleFunctionDeployResponse = (GoogleFunctionDeployResponse) responseDataSupplier.get();
        } catch (Exception e) {
            log.error("Error while processing serverless task response: {}", e.getMessage(), e);
            return googleFunctionsHelper.handleTaskException(ambiance, googleFunctionsStepPassThroughData, e);
        }
        StepResponse.StepResponseBuilder stepResponseBuilder =
                StepResponse.builder().unitProgressList(googleFunctionDeployResponse.getUnitProgressData().getUnitProgresses());
        if (googleFunctionDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
            return googleFunctionsHelper.getFailureResponseBuilder(googleFunctionDeployResponse, stepResponseBuilder)
                    .build();
        }

        return stepResponseBuilder.status(Status.SUCCEEDED).build();
    }

    @Override
    public TaskChainResponse startChainLinkAfterRbac(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
        return googleFunctionsHelper.startChainLink(this, ambiance, stepParameters);
    }
}
