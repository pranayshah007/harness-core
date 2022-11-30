package io.harness.cdng.tas;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.pcf.TasEntityHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.TasAppResizeDataOutcome;
import io.harness.cdng.tas.TasRollbackStepParameters;
import io.harness.cdng.tas.beans.TasSetupDataOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfSwapRollbackCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRollbackCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

import static software.wings.beans.TaskType.CF_COMMAND_TASK_NG;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasSwapRollbackStep extends TaskExecutableWithRollbackAndRbac<CfCommandResponseNG> {
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private TasEntityHelper tasEntityHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  public static final String TAS_SWAP_ROLLBACK = "SwapRollback";
  public static final String COMMAND_UNIT = "SwapRollback";
  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.TAS_NG)) {
      throw new AccessDeniedException(
          "TAS_NG FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }
  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters = (TasSwapRollbackStepParameters) stepParameters.getSpec();

    if (EmptyPredicate.isEmpty(tasSwapRollbackStepParameters.getTasRollbackFqn())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Tas rollback Step was not executed. Skipping .").build())
          .build();
    }

    OptionalSweepingOutput tasAppResizeDataOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
                tasSwapRollbackStepParameters.getTasRollbackFqn() + "." + OutcomeExpressionConstants.TAS_APP_RESIZE_OUTCOME));

    if (!tasAppResizeDataOptional.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Tas App resize Step was not executed. Skipping .").build())
          .build();
    }
    TasAppResizeDataOutcome tasAppResizeDataOutcome = (TasAppResizeDataOutcome) tasAppResizeDataOptional.getOutput();
    OptionalSweepingOutput tasSetupDataOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
                tasSwapRollbackStepParameters.getTasSetupFqn() + "." + OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME));

    if (!tasSetupDataOptional.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Tas Setup Step was not executed. Skipping .").build())
          .build();
    }
    TasSetupDataOutcome tasSetupDataOutcome =
        (io.harness.cdng.tas.beans.TasSetupDataOutcome) tasSetupDataOptional.getOutput();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    TasInfraConfig tasInfraConfig = getTasInfraConfig(ambiance);

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
            CfSwapRollbackCommandRequestNG.builder()
            .accountId(accountId)
            .existingApplicationDetails(tasSetupDataOutcome.getAppDetailsToBeDownsized())
            .commandName(TAS_SWAP_ROLLBACK)
            .cfAppNamePrefix(tasSetupDataOutcome.getCfAppNamePrefix())
            .cfCliVersion(tasSetupDataOutcome.getCfCliVersion())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .instanceData(tasAppResizeDataOutcome.getInstanceData())
            .tasInfraConfig(tasInfraConfig)
            .pcfCommandType(CfCommandTypeNG.SWAP_ROLLBACK)
            .timeoutIntervalInMin(10)
            .useAppAutoscalar(tasSetupDataOutcome.isUseAppAutoscalar())
            .oldApplicationDetails(tasSetupDataOutcome.getOldApplicationDetails())
            .newApplicationDetails(tasSetupDataOutcome.getNewApplicationDetails())
            .tempRouteMaps(tasSetupDataOutcome.getTempRouteMap())
            .routeMaps(tasSetupDataOutcome.getRouteMaps())
            .appDetailsToBeDownsized(tasSetupDataOutcome.getAppDetailsToBeDownsized())
            .upsizeInActiveApp(tasSwapRollbackStepParameters.getUpsizeInActiveApp().getValue())
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                  .taskType(CF_COMMAND_TASK_NG.name())
                                  .parameters(new Object[] {cfRollbackCommandRequestNG})
                                  .build();
    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer, Collections.singletonList(COMMAND_UNIT),
        CF_COMMAND_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(tasSwapRollbackStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private TasInfraConfig getTasInfraConfig(Ambiance ambiance) {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    BaseNGAccess baseNGAccess = tasEntityHelper.getBaseNGAccess(accountId, orgId, projectId);
    ConnectorInfoDTO connectorInfoDTO =
        tasEntityHelper.getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), baseNGAccess);
    return TasInfraConfig.builder()
        .organization(infrastructureOutcome.getOrganization())
        .space(infrastructureOutcome.getSpace())
        .encryptionDataDetails(tasEntityHelper.getEncryptionDataDetails(connectorInfoDTO, baseNGAccess))
        .tasConnectorDTO((TasConnectorDTO) connectorInfoDTO.getConnectorConfig())
        .build();
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<CfCommandResponseNG> responseDataSupplier)
      throws Exception {
    StepResponse.StepResponseBuilder builder = StepResponse.builder();

    CfRollbackCommandResponseNG response;
    try {
      response = (CfRollbackCommandResponseNG) responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    builder.unitProgressList(response.getUnitProgressData().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }
  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
