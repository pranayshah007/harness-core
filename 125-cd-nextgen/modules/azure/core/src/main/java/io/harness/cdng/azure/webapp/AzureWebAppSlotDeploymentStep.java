/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;
import static io.harness.azure.model.AzureConstants.FETCH_ARTIFACT_FILE;
import static io.harness.azure.model.AzureConstants.SAVE_EXISTING_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.UPDATE_SLOT_CONFIGURATION_SETTINGS;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.APPLICATION_SETTINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.CONNECTION_STRINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_COMMAND;
import static io.harness.common.ParameterFieldHelper.getBooleanParameterFieldValue;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.azure.webapp.beans.AzureSlotDeploymentPassThroughData;
import io.harness.cdng.azure.webapp.beans.AzureSlotDeploymentPassThroughData.AzureSlotDeploymentPassThroughDataBuilder;
import io.harness.cdng.azure.webapp.beans.AzureWebAppPreDeploymentDataOutput;
import io.harness.cdng.azure.webapp.beans.AzureWebAppSlotDeploymentDataOutput;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails.AzureWebAppsStageExecutionDetailsKeys;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.mapper.AzureWebAppToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppFetchPreDeploymentDataRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSlotDeploymentRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppFetchPreDeploymentDataResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppSlotDeploymentResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureArtifactType;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AZURE_WEBAPP})
public class AzureWebAppSlotDeploymentStep extends TaskChainExecutableWithRollbackAndRbac {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_SLOT_DEPLOYMENT.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @VisibleForTesting static final String FETCH_PREDEPLOYMENT_DATA_TASK_NAME = "Save App Service Configurations Task";
  @VisibleForTesting static final String V2_TASK_SUFFIX = " V2";

  @Inject private AzureWebAppStepHelper azureWebAppStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private StageExecutionInfoService stageExecutionInfoService;

  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    Map<String, StoreConfig> webAppConfig = azureWebAppStepHelper.fetchWebAppConfig(ambiance);
    InfrastructureOutcome infrastructure = cdStepHelper.getInfrastructureOutcome(ambiance);
    if (!(infrastructure instanceof AzureWebAppInfrastructureOutcome)) {
      throw new InvalidArgumentsException(Pair.of(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME,
          format("Invalid infrastructure outcome found. Expected: %s, found: %s", InfrastructureKind.AZURE_WEB_APP,
              infrastructure.getKind())));
    }

    ArtifactOutcome artifactOutcome = azureWebAppStepHelper.getPrimaryArtifactOutcome(ambiance);
    AzureSlotDeploymentPassThroughDataBuilder passThroughDataBuilder =
        AzureSlotDeploymentPassThroughData.builder()
            .infrastructure((AzureWebAppInfrastructureOutcome) infrastructure)
            .configs(emptyMap())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .unprocessedConfigs(emptyMap())
            .primaryArtifactOutcome(artifactOutcome)
            .taskType(azureWebAppStepHelper.getTaskTypeVersion(artifactOutcome));

    if (isNotEmpty(webAppConfig)) {
      return processAndFetchWebAppConfigs(
          stepParameters, ambiance, passThroughDataBuilder.unprocessedConfigs(webAppConfig).build());
    }

    return queueFetchPreDeploymentData(stepParameters, ambiance, passThroughDataBuilder.configs(emptyMap()).build());
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    ResponseData responseData = responseSupplier.get();
    AzureSlotDeploymentPassThroughData azureSlotDeploymentPassThroughData =
        (AzureSlotDeploymentPassThroughData) passThroughData;
    AzureSlotDeploymentPassThroughDataBuilder newPassThroughDataBuilder =
        azureSlotDeploymentPassThroughData.toBuilder();

    if (responseData instanceof GitFetchResponse) {
      GitFetchResponse gitFetchResponse = (GitFetchResponse) responseData;
      Map<String, AppSettingsFile> resultConfigs =
          azureWebAppStepHelper.getConfigValuesFromGitFetchResponse(ambiance, gitFetchResponse);
      newPassThroughDataBuilder.configs(ImmutableMap.<String, AppSettingsFile>builder()
                                            .putAll(azureSlotDeploymentPassThroughData.getConfigs())
                                            .putAll(resultConfigs)
                                            .build());
      newPassThroughDataBuilder.commandUnitsProgress(
          UnitProgressDataMapper.toCommandUnitsProgress(gitFetchResponse.getUnitProgressData()));
    } else if (responseData instanceof AzureWebAppTaskResponse) {
      AzureWebAppTaskResponse azureWebAppTaskResponse = (AzureWebAppTaskResponse) responseData;
      newPassThroughDataBuilder.commandUnitsProgress(
          UnitProgressDataMapper.toCommandUnitsProgress(azureWebAppTaskResponse.getCommandUnitsProgress()));
      if (azureWebAppTaskResponse.getRequestResponse() instanceof AzureWebAppFetchPreDeploymentDataResponse) {
        AzureWebAppFetchPreDeploymentDataResponse fetchPreDeploymentDataResponse =
            (AzureWebAppFetchPreDeploymentDataResponse) azureWebAppTaskResponse.getRequestResponse();
        newPassThroughDataBuilder.preDeploymentData(fetchPreDeploymentDataResponse.getPreDeploymentData());
        executionSweepingOutputService.consume(ambiance, AzureWebAppPreDeploymentDataOutput.OUTPUT_NAME,
            AzureWebAppPreDeploymentDataOutput.builder()
                .preDeploymentData(fetchPreDeploymentDataResponse.getPreDeploymentData())
                .build(),
            StepCategory.STEP.name());
      }
    }

    AzureSlotDeploymentPassThroughData newPassThroughData = newPassThroughDataBuilder.build();
    if (isNotEmpty(newPassThroughData.getUnprocessedConfigs())) {
      return processAndFetchWebAppConfigs(stepParameters, ambiance, newPassThroughDataBuilder.build());
    }

    if (newPassThroughData.getPreDeploymentData() == null) {
      return queueFetchPreDeploymentData(stepParameters, ambiance, newPassThroughDataBuilder.build());
    }

    return queueSlotDeploymentTask(stepParameters, ambiance, newPassThroughDataBuilder.build());
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    AzureWebAppTaskResponse webAppTaskResponse = (AzureWebAppTaskResponse) responseDataSupplier.get();
    AzureWebAppSlotDeploymentResponse slotDeploymentResponse =
        (AzureWebAppSlotDeploymentResponse) webAppTaskResponse.getRequestResponse();
    stepResponseBuilder.status(Status.SUCCEEDED);
    stepResponseBuilder.unitProgressList(webAppTaskResponse.getCommandUnitsProgress().getUnitProgresses());

    executionSweepingOutputService.consume(ambiance, AzureWebAppSlotDeploymentDataOutput.OUTPUT_NAME,
        AzureWebAppSlotDeploymentDataOutput.builder()
            .deploymentProgressMarker(slotDeploymentResponse.getDeploymentProgressMarker())
            .build(),
        StepCategory.STEP.name());
    AzureSlotDeploymentPassThroughData azureSlotDeploymentPassThroughData =
        (AzureSlotDeploymentPassThroughData) passThroughData;
    AzureArtifactConfig azureArtifactConfig = azureWebAppStepHelper.getPrimaryArtifactConfig(
        ambiance, azureSlotDeploymentPassThroughData.getPrimaryArtifactOutcome());
    updateStageExecutionDetails(ambiance, azureArtifactConfig,
        azureSlotDeploymentPassThroughData.getPreDeploymentData(), slotDeploymentResponse);

    StepResponse.StepOutcome stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance,
        AzureWebAppToServerInstanceInfoMapper.toServerInstanceInfoList(
            slotDeploymentResponse.getAzureAppDeploymentData()));

    return stepResponseBuilder.stepOutcome(stepOutcome).build();
  }

  private void updateStageExecutionDetails(Ambiance ambiance, AzureArtifactConfig azureArtifactConfig,
      AzureAppServicePreDeploymentData preDeploymentData, AzureWebAppSlotDeploymentResponse response) {
    Scope scope = Scope.of(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));
    Map<String, Object> updates = new HashMap<>();
    updates.put(StageExecutionInfoKeys.deploymentIdentifier,
        azureWebAppStepHelper.getDeploymentIdentifier(
            ambiance, preDeploymentData.getAppName(), preDeploymentData.getSlotName()));
    updates.put(String.format("%s.%s", StageExecutionInfoKeys.executionDetails,
                    AzureWebAppsStageExecutionDetailsKeys.userAddedAppSettingNames),
        response.getUserAddedAppSettingNames());
    updates.put(String.format("%s.%s", StageExecutionInfoKeys.executionDetails,
                    AzureWebAppsStageExecutionDetailsKeys.userAddedConnStringNames),
        response.getUserAddedConnStringNames());
    updates.put(String.format("%s.%s", StageExecutionInfoKeys.executionDetails,
                    AzureWebAppsStageExecutionDetailsKeys.userChangedStartupCommand),
        response.isUserChangedStartupCommand());
    if (AzureArtifactType.PACKAGE == azureArtifactConfig.getArtifactType()) {
      updates.put(String.format("%s.%s", StageExecutionInfoKeys.executionDetails,
                      AzureWebAppsStageExecutionDetailsKeys.artifactConfig),
          azureArtifactConfig);
    }

    stageExecutionInfoService.update(scope, ambiance.getStageExecutionId(), updates);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  private TaskChainResponse processAndFetchWebAppConfigs(
      StepBaseParameters stepElementParameters, Ambiance ambiance, AzureSlotDeploymentPassThroughData passThroughData) {
    AzureSlotDeploymentPassThroughDataBuilder newPassThroughDataBuilder = passThroughData.toBuilder();
    Map<String, StoreConfig> unprocessedConfigs = passThroughData.getUnprocessedConfigs();
    Map<String, GitStoreConfig> gitStoreConfigs =
        AzureWebAppStepHelper.filterAndMapConfigs(unprocessedConfigs, ManifestStoreType::isInGitSubset);

    if (isNotEmpty(gitStoreConfigs)) {
      newPassThroughDataBuilder.unprocessedConfigs(
          AzureWebAppStepHelper.getConfigDifference(unprocessedConfigs, gitStoreConfigs));
      return TaskChainResponse.builder()
          .chainEnd(false)
          .passThroughData(newPassThroughDataBuilder.build())
          .taskRequest(azureWebAppStepHelper.prepareGitFetchTaskRequest(
              stepElementParameters, ambiance, gitStoreConfigs, getCommandUnits(passThroughData, true)))
          .build();
    }

    Map<String, HarnessStore> harnessStoreConfigs =
        AzureWebAppStepHelper.filterAndMapConfigs(unprocessedConfigs, HARNESS_STORE_TYPE::equals);
    if (isNotEmpty(harnessStoreConfigs)) {
      Map<String, AppSettingsFile> configs =
          azureWebAppStepHelper.fetchWebAppConfigsFromHarnessStore(ambiance, harnessStoreConfigs);
      newPassThroughDataBuilder.unprocessedConfigs(
          AzureWebAppStepHelper.getConfigDifference(unprocessedConfigs, harnessStoreConfigs));
      newPassThroughDataBuilder.configs(
          ImmutableMap.<String, AppSettingsFile>builder().putAll(passThroughData.getConfigs()).putAll(configs).build());
    }

    if (passThroughData.getPreDeploymentData() == null) {
      return queueFetchPreDeploymentData(stepElementParameters, ambiance, newPassThroughDataBuilder.build());
    } else {
      return queueSlotDeploymentTask(stepElementParameters, ambiance, newPassThroughDataBuilder.build());
    }
  }

  private TaskChainResponse queueFetchPreDeploymentData(
      StepBaseParameters stepElementParameters, Ambiance ambiance, AzureSlotDeploymentPassThroughData passThroughData) {
    if (isNotEmpty(passThroughData.getUnprocessedConfigs())) {
      String unprocessedConfigsRepr = passThroughData.getUnprocessedConfigs()
                                          .entrySet()
                                          .stream()
                                          .map(entry -> format("{%s: %s}", entry.getKey(), entry.getValue().getKind()))
                                          .collect(Collectors.joining(", "));
      log.warn("Unexpected unprocessed configuration: [{}]", unprocessedConfigsRepr);
    }

    AzureWebAppSlotDeploymentStepParameters azureWebAppSlotDeploymentStepParameters =
        (AzureWebAppSlotDeploymentStepParameters) stepElementParameters.getSpec();
    AzureWebAppFetchPreDeploymentDataRequest fetchPreDeploymentDataRequest =
        AzureWebAppFetchPreDeploymentDataRequest.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .infraDelegateConfig(
                azureWebAppStepHelper.getInfraDelegateConfig(ambiance, passThroughData.getInfrastructure(),
                    getParameterFieldValue(azureWebAppSlotDeploymentStepParameters.getWebApp()),
                    getParameterFieldValue(azureWebAppSlotDeploymentStepParameters.getDeploymentSlot())))
            .applicationSettings(passThroughData.getConfigs().get(APPLICATION_SETTINGS))
            .connectionStrings(passThroughData.getConfigs().get(CONNECTION_STRINGS))
            .startupCommand(passThroughData.getConfigs().get(STARTUP_COMMAND))
            .artifact(
                azureWebAppStepHelper.getPrimaryArtifactConfig(ambiance, passThroughData.getPrimaryArtifactOutcome()))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .commandUnitsProgress(passThroughData.getCommandUnitsProgress())
            .build();

    TaskType taskType = isNotEmpty(passThroughData.getTaskType()) ? TaskType.valueOf(passThroughData.getTaskType())
                                                                  : TaskType.AZURE_WEB_APP_TASK_NG;
    String taskDisplayName = TaskType.AZURE_WEB_APP_TASK_NG_V2 == taskType
        ? FETCH_PREDEPLOYMENT_DATA_TASK_NAME + V2_TASK_SUFFIX
        : FETCH_PREDEPLOYMENT_DATA_TASK_NAME;
    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(azureWebAppStepHelper.prepareTaskRequest(stepElementParameters, ambiance,
            fetchPreDeploymentDataRequest, taskType, taskDisplayName, getCommandUnits(passThroughData, false)))
        .passThroughData(passThroughData)
        .build();
  }

  private TaskChainResponse queueSlotDeploymentTask(
      StepBaseParameters stepElementParameters, Ambiance ambiance, AzureSlotDeploymentPassThroughData passThroughData) {
    AzureWebAppSlotDeploymentStepParameters azureWebAppSlotDeploymentStepParameters =
        (AzureWebAppSlotDeploymentStepParameters) stepElementParameters.getSpec();
    AzureWebAppInfraDelegateConfig infraDelegateConfig =
        azureWebAppStepHelper.getInfraDelegateConfig(ambiance, passThroughData.getInfrastructure(),
            getParameterFieldValue(azureWebAppSlotDeploymentStepParameters.getWebApp()),
            getParameterFieldValue(azureWebAppSlotDeploymentStepParameters.getDeploymentSlot()));
    AzureWebAppsStageExecutionDetails prevExecutionDetails =
        azureWebAppStepHelper.findLastSuccessfulStageExecutionDetails(ambiance, infraDelegateConfig);

    boolean cleanDeployment =
        cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_WEBAPP_ENABLE_CLEAN_OPTION)
        && getBooleanParameterFieldValue(azureWebAppSlotDeploymentStepParameters.getClean());

    AzureWebAppSlotDeploymentRequest slotDeploymentRequest =
        AzureWebAppSlotDeploymentRequest.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .preDeploymentData(passThroughData.getPreDeploymentData())
            .applicationSettings(passThroughData.getConfigs().get(APPLICATION_SETTINGS))
            .connectionStrings(passThroughData.getConfigs().get(CONNECTION_STRINGS))
            .startupCommand(passThroughData.getConfigs().get(STARTUP_COMMAND))
            .infrastructure(infraDelegateConfig)
            .artifact(
                azureWebAppStepHelper.getPrimaryArtifactConfig(ambiance, passThroughData.getPrimaryArtifactOutcome()))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .commandUnitsProgress(passThroughData.getCommandUnitsProgress())
            .prevExecUserAddedAppSettingsNames(
                prevExecutionDetails != null ? prevExecutionDetails.getUserAddedAppSettingNames() : null)
            .prevExecUserAddedConnStringsNames(
                prevExecutionDetails != null ? prevExecutionDetails.getUserAddedConnStringNames() : null)
            .prevExecUserChangedStartupCommand(prevExecutionDetails != null
                && Boolean.TRUE.equals(prevExecutionDetails.getUserChangedStartupCommand()))
            .cleanDeployment(cleanDeployment)
            .build();

    TaskType taskType = isNotEmpty(passThroughData.getTaskType()) ? TaskType.valueOf(passThroughData.getTaskType())
                                                                  : TaskType.AZURE_WEB_APP_TASK_NG;
    return TaskChainResponse.builder()
        .chainEnd(true)
        .taskRequest(azureWebAppStepHelper.prepareTaskRequest(
            stepElementParameters, ambiance, slotDeploymentRequest, taskType, getCommandUnits(passThroughData, false)))
        .passThroughData(passThroughData)
        .build();
  }

  private List<String> getCommandUnits(AzureSlotDeploymentPassThroughData passThroughData, boolean fetchFiles) {
    List<String> units = new ArrayList<>();
    if (fetchFiles) {
      units.add(FetchFiles);
    }

    units.add(SAVE_EXISTING_CONFIGURATIONS);
    if (azureWebAppStepHelper.isPackageArtifactType(passThroughData.getPrimaryArtifactOutcome())) {
      units.add(FETCH_ARTIFACT_FILE);
    }

    units.add(UPDATE_SLOT_CONFIGURATION_SETTINGS);
    units.add(DEPLOY_TO_SLOT);
    return units;
  }
}
