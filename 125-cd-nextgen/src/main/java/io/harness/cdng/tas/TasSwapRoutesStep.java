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
import io.harness.cdng.tas.outcome.TasSwapRouteDataOutcome;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfSwapRouteCommandResult;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfSwapRoutesRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfSwapRouteCommandResponseNG;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasSwapRoutesStep extends CdTaskExecutable<CfCommandResponseNG> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TAS_SWAP_ROUTES.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private TasEntityHelper tasEntityHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private TasStepHelper tasStepHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private InstanceInfoService instanceInfoService;
  public static final String COMMAND_UNIT = "Tas Swap Routes";
  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_SVC_ENV_REDESIGN)) {
      throw new AccessDeniedException(
          "CDS_TAS_NG FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }
  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, ThrowingSupplier<CfCommandResponseNG> responseDataSupplier)
      throws Exception {
    StepResponseBuilder builder = StepResponse.builder();

    CfCommandResponseNG response;
    try {
      response = responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }

    TasSwapRoutesStepParameters tasSwapRoutesStepParameters = (TasSwapRoutesStepParameters) stepParameters.getSpec();

    if (!response.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TAS_SWAP_ROUTES_OUTCOME,
          TasSwapRouteDataOutcome.builder()
              .swapRouteOccurred(false)
              .downsizeOldApplication(
                  ParameterFieldHelper.getParameterFieldValue(tasSwapRoutesStepParameters.getDownSizeOldApplication()))
              .build(),
          StepCategory.STEP.name());
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().setErrorMessage(TasStepHelper.getErrorMessage(response)).build())
          .unitProgressList(
              tasStepHelper
                  .completeUnitProgressData(response.getUnitProgressData(), ambiance, response.getErrorMessage())
                  .getUnitProgresses())
          .build();
    }

    OptionalSweepingOutput tasAppResizeDataOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            tasSwapRoutesStepParameters.getTasResizeFqn() + "." + OutcomeExpressionConstants.TAS_APP_RESIZE_OUTCOME));

    TasSwapRouteDataOutcome tasSwapRouteDataOutcome =
        TasSwapRouteDataOutcome.builder()
            .swapRouteOccurred(true)
            .downsizeOldApplication(
                ParameterFieldHelper.getParameterFieldValue(tasSwapRoutesStepParameters.getDownSizeOldApplication()))
            .build();

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TAS_SWAP_ROUTES_OUTCOME,
        tasSwapRouteDataOutcome, StepCategory.STEP.name());
    List<ServerInstanceInfo> serverInstanceInfoList = Collections.emptyList();
    CfSwapRouteCommandResult cfSwapRouteCommandResult =
        ((CfSwapRouteCommandResponseNG) response).getCfSwapRouteCommandResult();
    if (isNull(cfSwapRouteCommandResult)) {
      // for backward compatibility
      if (tasAppResizeDataOptional.isFound()) {
        serverInstanceInfoList = getServerInstanceInfoListFromAppResizeData(
            ((CfSwapRouteCommandResponseNG) response).getNewApplicationName(),
            (TasAppResizeDataOutcome) tasAppResizeDataOptional.getOutput(), ambiance);
      }
    } else {
      serverInstanceInfoList = getServerInstanceInfoList(cfSwapRouteCommandResult, ambiance);
    }

    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);

    builder.unitProgressList(response.getUnitProgressData().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    builder.stepOutcome(stepOutcome);
    return builder.build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoListFromAppResizeData(
      String newApplicationName, TasAppResizeDataOutcome response, Ambiance ambiance) {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    List<CfInternalInstanceElement> instances = response.getCfInstanceElements();
    return instances.stream()
        .map(instance -> getServerInstance(newApplicationName, instance, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TasSwapRoutesStepParameters tasSwapRoutesStepParameters = (TasSwapRoutesStepParameters) stepParameters.getSpec();
    OptionalSweepingOutput tasSetupDataOptional =
        tasEntityHelper.getSetupOutcome(ambiance, tasSwapRoutesStepParameters.getTasBGSetupFqn(),
            tasSwapRoutesStepParameters.getTasBasicSetupFqn(), tasSwapRoutesStepParameters.getTasCanarySetupFqn(),
            OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME, executionSweepingOutputService);

    if (!tasSetupDataOptional.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Tas Swap Route Step was not executed. Skipping.").build())
          .build();
    }
    TasSetupDataOutcome tasSetupDataOutcome = (TasSetupDataOutcome) tasSetupDataOptional.getOutput();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    TasInfraConfig tasInfraConfig = getTasInfraConfig(ambiance);
    List<String> existingAppNames = new ArrayList<>();
    if (!isNull(tasSetupDataOutcome.getActiveApplicationDetails())
        && !isNull(tasSetupDataOutcome.getActiveApplicationDetails().getApplicationName())) {
      existingAppNames =
          Collections.singletonList(tasSetupDataOutcome.getActiveApplicationDetails().getApplicationName());
    }

    boolean downSizeOldApplication = tasSwapRoutesStepParameters.getDownSizeOldApplication().getValue();
    CfSwapRoutesRequestNG cfSwapRoutesRequestNG =
        CfSwapRoutesRequestNG.builder()
            .finalRoutes(tasSetupDataOutcome.getRouteMaps())
            .useCfCLI(true)
            .downsizeOldApplication(downSizeOldApplication)
            .existingApplicationNames(existingAppNames)
            .accountId(accountId)
            .newApplicationDetails(tasSetupDataOutcome.getNewApplicationDetails() == null
                    ? null
                    : tasSetupDataOutcome.getNewApplicationDetails().cloneObject())
            .activeApplicationDetails(tasSetupDataOutcome.getActiveApplicationDetails() == null
                    ? null
                    : tasSetupDataOutcome.getActiveApplicationDetails().cloneObject())
            .inActiveApplicationDetails(tasSetupDataOutcome.getInActiveApplicationDetails() == null
                    ? null
                    : tasSetupDataOutcome.getInActiveApplicationDetails().cloneObject())
            .releaseNamePrefix(tasSetupDataOutcome.getCfAppNamePrefix())
            .commandName(CfCommandTypeNG.SWAP_ROUTES.toString())
            .cfCliVersion(tasSetupDataOutcome.getCfCliVersion())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .tempRoutes(tasSetupDataOutcome.getTempRouteMap())
            .newApplicationName(getNewApplicationName(tasSetupDataOutcome))
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROUTES)
            .tasInfraConfig(tasInfraConfig)
            .timeoutIntervalInMin(tasSetupDataOutcome.getTimeoutIntervalInMinutes())
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                  .taskType(TaskType.TAS_SWAP_ROUTES.name())
                                  .parameters(new Object[] {cfSwapRoutesRequestNG})
                                  .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        Arrays.asList(CfCommandUnitConstants.SwapRoutesForNewApplication,
            CfCommandUnitConstants.SwapRoutesForExistingApplication, CfCommandUnitConstants.Downsize,
            CfCommandUnitConstants.Rename, CfCommandUnitConstants.Wrapup),
        TaskType.TAS_SWAP_ROUTES.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(tasSwapRoutesStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private String getNewApplicationName(TasSetupDataOutcome tasSetupDataOutcome) {
    if (tasSetupDataOutcome.getNewApplicationDetails() != null) {
      return tasSetupDataOutcome.getNewApplicationDetails().getApplicationName();
    }
    return null;
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

  private List<ServerInstanceInfo> getServerInstanceInfoList(CfSwapRouteCommandResult response, Ambiance ambiance) {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    List<CfInternalInstanceElement> instances = new ArrayList<>(Collections.emptyList());
    instances.addAll(response.getNewAppInstances());
    return instances.stream()
        .map(instance -> getServerInstance(null, instance, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private ServerInstanceInfo getServerInstance(String newAppName, CfInternalInstanceElement instance,
      TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome) {
    return TasServerInstanceInfo.builder()
        .id(instance.getApplicationId() + ":" + instance.getInstanceIndex())
        .instanceIndex(instance.getInstanceIndex())
        .tasApplicationName(isNull(newAppName) ? instance.getDisplayName() : newAppName)
        .tasApplicationGuid(instance.getApplicationId())
        .organization(infrastructureOutcome.getOrganization())
        .space(infrastructureOutcome.getSpace())
        .build();
  }
}
