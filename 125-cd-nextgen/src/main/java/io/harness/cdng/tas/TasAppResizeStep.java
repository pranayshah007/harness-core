/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;

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
import io.harness.delegate.beans.pcf.CfDeployCommandResult;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.TasResizeStrategyType;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasAppResizeStep extends CdTaskExecutable<CfCommandResponseNG> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TAS_APP_RESIZE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private TasEntityHelper tasEntityHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private TasStepHelper tasStepHelper;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  public static final String COMMAND_UNIT = "Tas App resize";
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
    CfDeployCommandResponseNG response;
    try {
      response = (CfDeployCommandResponseNG) responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    if (!CommandExecutionStatus.SUCCESS.equals(response.getCommandExecutionStatus())) {
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().setErrorMessage(response.getErrorMessage()).build())
          .unitProgressList(response.getUnitProgressData().getUnitProgresses())
          .build();
    }
    TasAppResizeDataOutcome tasAppResizeDataOutcome =
        TasAppResizeDataOutcome.builder()
            .instanceData(response.getCfDeployCommandResult().getInstanceDataUpdated())
            .cfInstanceElements(response.getCfDeployCommandResult().getNewAppInstances())
            .build();
    if (!response.getCfDeployCommandResult().isStandardBG()) {
      List<ServerInstanceInfo> serverInstanceInfoList = getServerInstanceInfoList(response, ambiance);
      StepResponse.StepOutcome stepOutcome =
          instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
      builder.stepOutcome(stepOutcome);
    }
    executionSweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.TAS_APP_RESIZE_OUTCOME, tasAppResizeDataOutcome, StepCategory.STEP.name());

    builder.stepOutcome(StepResponse.StepOutcome.builder()
                            .name(OutcomeExpressionConstants.OUTPUT)
                            .outcome(tasAppResizeDataOutcome)
                            .build());
    builder.unitProgressList(response.getUnitProgressData().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(CfDeployCommandResponseNG response, Ambiance ambiance) {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    CfDeployCommandResult cfDeployCommandResult = response.getCfDeployCommandResult();
    if (cfDeployCommandResult == null) {
      log.error("Could not generate server instance info for app resize step");
      return Collections.emptyList();
    }
    List<CfInternalInstanceElement> instances = cfDeployCommandResult.getNewAppInstances();
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
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    TasAppResizeStepParameters tasAppResizeStepParameters = (TasAppResizeStepParameters) stepParameters.getSpec();

    OptionalSweepingOutput tasSetupDataOptional =
        tasEntityHelper.getSetupOutcome(ambiance, tasAppResizeStepParameters.getTasBGSetupFqn(),
            tasAppResizeStepParameters.getTasBasicSetupFqn(), tasAppResizeStepParameters.getTasCanarySetupFqn(),
            OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME, executionSweepingOutputService);

    if (!tasSetupDataOptional.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Tas App resize Step was not executed. Skipping .").build())
          .build();
    }
    Integer upsizeInstanceCount = getValue(tasAppResizeStepParameters.getNewAppInstances());
    Integer downsizeInstanceCount = null;
    if (!isNull(tasAppResizeStepParameters.getOldAppInstances())) {
      downsizeInstanceCount = getValue(tasAppResizeStepParameters.getOldAppInstances());
    }
    TasInstanceUnitType upsizeInstanceCountType = tasAppResizeStepParameters.getNewAppInstances().getType();
    TasInstanceUnitType downsizeCountType = isNull(tasAppResizeStepParameters.getOldAppInstances())
        ? null
        : tasAppResizeStepParameters.getOldAppInstances().getType();
    TasSetupDataOutcome tasSetupDataOutcome = (TasSetupDataOutcome) tasSetupDataOptional.getOutput();
    Integer totalDesiredCount = tasSetupDataOutcome.getDesiredActualFinalCount();
    Boolean ignoreInstanceCountManifest = isNull(tasAppResizeStepParameters.getIgnoreInstanceCountManifest())
        ? Boolean.FALSE
        : tasAppResizeStepParameters.getIgnoreInstanceCountManifest().getValue();
    Integer upsizeCount = getUpsizeCountV2(upsizeInstanceCount, upsizeInstanceCountType, totalDesiredCount,
        ignoreInstanceCountManifest, tasSetupDataOutcome.getInstanceCountType());
    Integer downsizeCount = getDownsizeCount(downsizeCountType, downsizeInstanceCount, totalDesiredCount, upsizeCount);
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    BaseNGAccess baseNGAccess = tasEntityHelper.getBaseNGAccess(accountId, orgId, projectId);
    ConnectorInfoDTO connectorInfoDTO =
        tasEntityHelper.getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), accountId, orgId, projectId);
    TasInfraConfig tasInfraConfig =
        TasInfraConfig.builder()
            .organization(infrastructureOutcome.getOrganization())
            .space(infrastructureOutcome.getSpace())
            .encryptionDataDetails(tasEntityHelper.getEncryptionDataDetails(connectorInfoDTO, baseNGAccess))
            .tasConnectorDTO((TasConnectorDTO) connectorInfoDTO.getConnectorConfig())
            .build();
    CfDeployCommandRequestNG cfDeployCommandRequestNG =
        CfDeployCommandRequestNG.builder()
            .accountId(accountId)
            .commandName(CfCommandTypeNG.APP_RESIZE.name())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .cfCliVersion(tasSetupDataOutcome.getCfCliVersion())
            .downsizeAppDetail(isNull(tasSetupDataOutcome.getActiveApplicationDetails())
                    ? null
                    : tasSetupDataOutcome.getActiveApplicationDetails())
            .isStandardBlueGreen(tasSetupDataOutcome.getIsBlueGreen())
            .upsizeCount(upsizeCount)
            .downSizeCount(downsizeCount)
            .instanceData(tasSetupDataOutcome.getInstanceData())
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .resizeStrategy(tasSetupDataOutcome.getResizeStrategy())
            .newReleaseName(tasSetupDataOutcome.getNewReleaseName())
            .tasInfraConfig(tasInfraConfig)
            .tasManifestsPackage(tasSetupDataOutcome.getManifestsPackage())
            .maxCount(tasSetupDataOutcome.getMaxCount())
            .useAppAutoScalar(tasSetupDataOutcome.isUseAppAutoScalar())
            .timeoutIntervalInMin(tasSetupDataOutcome.getTimeoutIntervalInMinutes())
            .useCfCLI(true)
            .totalPreviousInstanceCount(tasSetupDataOutcome.getTotalPreviousInstanceCount())
            .build();
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                                  .taskType(TaskType.TAS_APP_RESIZE.name())
                                  .parameters(new Object[] {cfDeployCommandRequestNG})
                                  .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        getCommandUnitList(tasSetupDataOutcome.getResizeStrategy()), TaskType.TAS_APP_RESIZE.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(tasAppResizeStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @NotNull
  private Integer getValue(TasInstanceSelectionWrapper tasInstanceSelectionWrapper) {
    if (tasInstanceSelectionWrapper.getType().equals(TasInstanceUnitType.COUNT)) {
      return new BigDecimal(
          getParameterFieldValue(((TasCountInstanceSelection) tasInstanceSelectionWrapper.getSpec()).getValue()))
          .intValueExact();
    } else {
      return new BigDecimal(
          getParameterFieldValue(((TasPercentageInstanceSelection) tasInstanceSelectionWrapper.getSpec()).getValue()))
          .intValueExact();
    }
  }

  private List<String> getCommandUnitList(TasResizeStrategyType resizeStrategy) {
    List<String> commandUnitList = new ArrayList<>();
    if (TasResizeStrategyType.UPSCALE_NEW_FIRST.equals(resizeStrategy)) {
      commandUnitList.add(CfCommandUnitConstants.Upsize);
      commandUnitList.add(CfCommandUnitConstants.Downsize);
    } else {
      commandUnitList.add(CfCommandUnitConstants.Downsize);
      commandUnitList.add(CfCommandUnitConstants.Upsize);
    }
    commandUnitList.add(CfCommandUnitConstants.Wrapup);
    return commandUnitList;
  }

  private Integer getDownsizeCount(TasInstanceUnitType downsizeCountType, Integer downsizeInstanceCount,
      Integer totalDesiredCount, Integer upsizeCount) {
    if (downsizeInstanceCount == null) {
      return Math.max(totalDesiredCount - upsizeCount, 0);
    } else {
      if (downsizeCountType == TasInstanceUnitType.PERCENTAGE) {
        int percent = Math.min(downsizeInstanceCount, 100);
        int count = (int) Math.round((percent * totalDesiredCount) / 100.0);
        return Math.max(count, 0);

      } else {
        return Math.max(downsizeInstanceCount, 0);
      }
    }
  }

  private Integer getUpsizeCountV2(Integer upsizeInstanceCount, TasInstanceUnitType upsizeInstanceCountType,
      Integer totalDesiredCount, Boolean ignoreInstanceCountManifest, TasInstanceCountType instanceCountType) {
    if (TasInstanceCountType.MATCH_RUNNING_INSTANCES.equals(instanceCountType)) {
      return getUpsizeCountV1(upsizeInstanceCount, upsizeInstanceCountType, totalDesiredCount);
    } else {
      if (ignoreInstanceCountManifest != null && ignoreInstanceCountManifest.booleanValue() == true) {
        if (upsizeInstanceCountType == TasInstanceUnitType.PERCENTAGE) {
          throw NestedExceptionUtils.hintWithExplanationException(
              "Disable Ignore instance count from manifest or change total instances type to Instance Count",
              "\nCant ignore manifest count for percentage instance count type",
              new InvalidRequestException("Invalid configuration for App Resize Step"));
        } else {
          return upsizeInstanceCount;
        }
      } else {
        return getUpsizeCountV1(upsizeInstanceCount, upsizeInstanceCountType, totalDesiredCount);
      }
    }
  }

  private Integer getUpsizeCountV1(
      Integer upsizeInstanceCount, TasInstanceUnitType upsizeInstanceCountType, Integer totalDesiredCount) {
    if (upsizeInstanceCountType == TasInstanceUnitType.PERCENTAGE) {
      int percent = Math.min(upsizeInstanceCount, 100);
      int count = (int) Math.round((percent * totalDesiredCount) / 100.0);
      return Math.max(count, 0);
    } else {
      return Math.min(totalDesiredCount, upsizeInstanceCount);
    }
  }
}
