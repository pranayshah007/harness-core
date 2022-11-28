package io.harness.cdng.pcf;

import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.beans.TaskType.CF_COMMAND_TASK_NG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.pcf.beans.TasSetupDataOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.temp.TasAppResizeDataOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponseNG;
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
import io.harness.pms.contracts.steps.StepCategory;
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

import software.wings.beans.InstanceUnitType;

import com.google.inject.Inject;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasAppResizeStep extends TaskExecutableWithRollbackAndRbac<CfCommandResponseNG> {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private TasEntityHelper tasEntityHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  public static final String TAS_APP_RESIZE = "TasAppResize";
  public static final String COMMAND_UNIT = "Tas App resize";
  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.TAS_NG)) {
      throw new AccessDeniedException(
          "TAS_NG FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }
  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<CfCommandResponseNG> responseDataSupplier) throws Exception {
    StepResponse.StepResponseBuilder builder = StepResponse.builder();

    CfDeployCommandResponseNG response;
    try {
      response = (CfDeployCommandResponseNG) responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TAS_APP_RESIZE_OUTCOME,
        TasAppResizeDataOutcome.builder()
            .instanceData(response.getCfDeployCommandResult().getInstanceDataUpdated())
            .build(),
        StepCategory.STEP.name());
    builder.unitProgressList(response.getUnitProgressData().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }
  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    io.harness.cdng.tas.TasAppResizeStepParameters tasAppResizeStepParameters =
        (io.harness.cdng.tas.TasAppResizeStepParameters) stepParameters.getSpec();

    if (EmptyPredicate.isEmpty(tasAppResizeStepParameters.getTasSetupFqn())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Tas App resize Step was not executed. Skipping .").build())
          .build();
    }

    OptionalSweepingOutput tasSetupDataOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            tasAppResizeStepParameters.getTasSetupFqn() + "." + OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME));

    if (!tasSetupDataOptional.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Tas App resize Step was not executed. Skipping .").build())
          .build();
    }

    Integer upsizeInstanceCount = tasAppResizeStepParameters.getUpsizeInstanceCount().getValue();
    Integer downsizeInstanceCount = tasAppResizeStepParameters.getDownsizeInstanceCount().getValue();
    InstanceUnitType upsizeInstanceCountType = tasAppResizeStepParameters.getUpsizeInstanceUnitType().getValue();
    InstanceUnitType downsizeCountType = tasAppResizeStepParameters.getDownsizeInstanceUnitType().getValue();
    TasSetupDataOutcome tasSetupDataOutcome = (TasSetupDataOutcome) tasSetupDataOptional.getOutput();
    Integer totalDesiredCount = tasSetupDataOutcome.getDesiredActualFinalCount();

    Integer upsizeCount = getUpsizeCount(upsizeInstanceCount, upsizeInstanceCountType, totalDesiredCount);
    Integer downsizeCount = getdownsizeCount(downsizeCountType, downsizeInstanceCount, totalDesiredCount, upsizeCount);

    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    BaseNGAccess baseNGAccess = tasEntityHelper.getBaseNGAccess(accountId, orgId, projectId);
    ConnectorInfoDTO connectorInfoDTO =
        tasEntityHelper.getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), baseNGAccess);
    TasInfraConfig tasInfraConfig =
        TasInfraConfig.builder()
            .organization(infrastructureOutcome.getOrganization())
            .space(infrastructureOutcome.getSpace())
            .encryptionDataDetails(tasEntityHelper.getEncryptionDataDetails(connectorInfoDTO, baseNGAccess))
            .tasConnectorDTO((TasConnectorDTO) connectorInfoDTO.getConnectorConfig())
            .build();
    // todo: timeout
    CfDeployCommandRequestNG cfDeployCommandRequestNG =
        CfDeployCommandRequestNG.builder()
            .accountId(accountId)
            .commandName(TAS_APP_RESIZE)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .cfCliVersion(tasSetupDataOutcome.getCfCliVersion())
            .downsizeAppDetail(tasSetupDataOutcome.getDownsizeAppDetail())
            .upsizeCount(upsizeCount)
            .downSizeCount(downsizeCount)
            .instanceData(tasSetupDataOutcome.getInstanceData())
            .cfCommandType(CfCommandTypeNG.APP_RESIZE)
            .resizeStrategy(tasSetupDataOutcome.getResizeStrategy())
            .newReleaseName(tasSetupDataOutcome.getNewReleaseName())
            .tasInfraConfig(tasInfraConfig)
            .useAppAutoscalar(tasSetupDataOutcome.isUseAppAutoscalar())
            .timeoutIntervalInMin(2)
            .totalPreviousInstanceCount(tasSetupDataOutcome.getTotalPreviousInstanceCount())
            .build();
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                  .taskType(CF_COMMAND_TASK_NG.name())
                                  .parameters(new Object[] {cfDeployCommandRequestNG})
                                  .build();
    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        Collections.singletonList(TasAppResizeStep.COMMAND_UNIT), CF_COMMAND_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(tasAppResizeStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private Integer getdownsizeCount(InstanceUnitType downsizeCountType, Integer downsizeInstanceCount,
      Integer totalDesiredCount, Integer upsizeCount) {
    if (downsizeInstanceCount == null) {
      return Math.max(totalDesiredCount - upsizeCount, 0);
    } else {
      if (downsizeCountType == PERCENTAGE) {
        int percent = Math.min(downsizeInstanceCount, 100);
        int count = (int) Math.round((percent * totalDesiredCount) / 100.0);
        return Math.max(count, 0);

      } else {
        return Math.max(downsizeInstanceCount, 0);
      }
    }
  }

  private Integer getUpsizeCount(
      Integer upsizeInstanceCount, InstanceUnitType upsizeInstanceCountType, Integer totalDesiredCount) {
    if (upsizeInstanceCountType == PERCENTAGE) {
      int percent = Math.min(upsizeInstanceCount, 100);
      int count = (int) Math.round((percent * totalDesiredCount) / 100.0);
      return Math.max(count, 1);
    } else {
      return Math.min(totalDesiredCount, upsizeInstanceCount);
    }
  }
}
