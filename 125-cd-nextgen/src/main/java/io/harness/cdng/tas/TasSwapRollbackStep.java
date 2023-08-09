/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.outcome.TasAppResizeDataOutcome;
import io.harness.cdng.tas.outcome.TasSetupDataOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfRollbackCommandResult;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfSwapRollbackCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRollbackCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasSwapRollbackStep extends CdTaskExecutable<CfCommandResponseNG> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SWAP_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private TasEntityHelper tasEntityHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private TasStepHelper tasStepHelper;
  @Inject private InstanceInfoService instanceInfoService;
  public static final String COMMAND_UNIT = "Swap Rollback";
  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_SVC_ENV_REDESIGN)) {
      throw new AccessDeniedException(
          "CDS_TAS_NG FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }
  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        (TasSwapRollbackStepParameters) stepParameters.getSpec();

    OptionalSweepingOutput tasSetupDataOptional =
        tasEntityHelper.getSetupOutcome(ambiance, tasSwapRollbackStepParameters.getTasBGSetupFqn(),
            tasSwapRollbackStepParameters.getTasBasicSetupFqn(), tasSwapRollbackStepParameters.getTasCanarySetupFqn(),
            OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME, executionSweepingOutputService);

    if (!tasSetupDataOptional.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Tas Setup Step was not executed. Skipping .").build())
          .build();
    }
    TasSetupDataOutcome tasSetupDataOutcome =
        (io.harness.cdng.tas.outcome.TasSetupDataOutcome) tasSetupDataOptional.getOutput();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    TasInfraConfig tasInfraConfig = getTasInfraConfig(ambiance);

    OptionalSweepingOutput tasAppResizeDataOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            tasSwapRollbackStepParameters.getTasResizeFqn() + "." + OutcomeExpressionConstants.TAS_APP_RESIZE_OUTCOME));

    TasAppResizeDataOutcome tasAppResizeDataOutcome = (TasAppResizeDataOutcome) tasAppResizeDataOptional.getOutput();
    List<CfServiceData> instanceData = new ArrayList<>();
    if (tasAppResizeDataOutcome != null && tasAppResizeDataOutcome.getInstanceData() != null) {
      tasAppResizeDataOutcome.getInstanceData().forEach(cfServiceData -> {
        int temp = cfServiceData.getDesiredCount();
        cfServiceData.setDesiredCount(cfServiceData.getPreviousCount());
        cfServiceData.setPreviousCount(temp);
        instanceData.add(cfServiceData);
      });
    }

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .accountId(accountId)
            .useCfCLI(true)
            .commandName(CfCommandTypeNG.SWAP_ROLLBACK.name())
            .cfAppNamePrefix(tasSetupDataOutcome.getCfAppNamePrefix())
            .cfCliVersion(tasSetupDataOutcome.getCfCliVersion())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .timeoutIntervalInMin(tasSetupDataOutcome.getTimeoutIntervalInMinutes())
            .downsizeOldApplication(true)
            .timeoutIntervalInMin(10)
            .swapRouteOccurred(true)
            .useAppAutoScalar(tasSetupDataOutcome.isUseAppAutoScalar())
            .activeApplicationDetails(tasSetupDataOutcome.getActiveApplicationDetails() == null
                    ? null
                    : tasSetupDataOutcome.getActiveApplicationDetails().cloneObject())
            .newApplicationDetails(tasSetupDataOutcome.getNewApplicationDetails() == null
                    ? tasSetupDataOutcome.getNewApplicationDetails()
                    : tasSetupDataOutcome.getNewApplicationDetails().cloneObject())
            .inActiveApplicationDetails(tasSetupDataOutcome.getInActiveApplicationDetails() == null
                    ? null
                    : tasSetupDataOutcome.getInActiveApplicationDetails().cloneObject())
            .tempRoutes(tasSetupDataOutcome.getTempRouteMap())
            .routeMaps(tasSetupDataOutcome.getRouteMaps())
            .instanceData(instanceData)
            .upsizeInActiveApp(tasSwapRollbackStepParameters.getUpsizeInActiveApp().getValue())
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                  .taskType(TaskType.TAS_SWAP_ROLLBACK.name())
                                  .parameters(new Object[] {cfRollbackCommandRequestNG})
                                  .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Arrays.asList(CfCommandUnitConstants.SwapRollback, CfCommandUnitConstants.Upsize,
            CfCommandUnitConstants.Downsize, CfCommandUnitConstants.Wrapup),
        TaskType.TAS_SWAP_ROLLBACK.getDisplayName(),
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
        tasEntityHelper.getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), accountId, orgId, projectId);
    return TasInfraConfig.builder()
        .organization(infrastructureOutcome.getOrganization())
        .space(infrastructureOutcome.getSpace())
        .encryptionDataDetails(tasEntityHelper.getEncryptionDataDetails(connectorInfoDTO, baseNGAccess))
        .tasConnectorDTO((TasConnectorDTO) connectorInfoDTO.getConnectorConfig())
        .build();
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<CfCommandResponseNG> responseDataSupplier)
      throws Exception {
    StepResponseBuilder builder = StepResponse.builder();

    CfRollbackCommandResponseNG response;
    try {
      response = (CfRollbackCommandResponseNG) responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    if (!response.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().setErrorMessage(response.getErrorMessage()).build())
          .unitProgressList(
              tasStepHelper
                  .completeUnitProgressData(response.getUnitProgressData(), ambiance, response.getErrorMessage())
                  .getUnitProgresses())
          .build();
    }
    List<ServerInstanceInfo> serverInstanceInfoList = getServerInstanceInfoList(response, ambiance);
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
    builder.stepOutcome(stepOutcome);
    builder.unitProgressList(response.getUnitProgressData().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(CfRollbackCommandResponseNG response, Ambiance ambiance) {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    CfRollbackCommandResult cfRollbackCommandResult = response.getCfRollbackCommandResult();
    if (cfRollbackCommandResult == null) {
      log.error("Could not generate server instance info for app resize step");
      return Collections.emptyList();
    }
    List<CfInternalInstanceElement> instances = cfRollbackCommandResult.getNewAppInstances();
    if (!isNull(instances)) {
      return instances.stream()
          .map(instance -> getServerInstance(instance, infrastructureOutcome))
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  private ServerInstanceInfo getServerInstance(
      CfInternalInstanceElement instance, TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome) {
    return TasServerInstanceInfo.builder()
        .id(instance.getApplicationId() + ":" + instance.getInstanceIndex())
        .instanceIndex(instance.getInstanceIndex())
        .tasApplicationName(instance.getDisplayName())
        .tasApplicationGuid(instance.getApplicationId())
        .organization(infrastructureOutcome.getOrganization())
        .space(infrastructureOutcome.getSpace())
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
