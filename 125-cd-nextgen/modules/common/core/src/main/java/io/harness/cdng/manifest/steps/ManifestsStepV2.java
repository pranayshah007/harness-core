/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps;
import static io.harness.cdng.manifest.ManifestType.MULTIPLE_SUPPORTED_MANIFEST_TYPES;
import static io.harness.cdng.service.steps.ServiceStepOverrideHelper.validateOverridesTypeAndUniqueness;
import static io.harness.cdng.service.steps.ServiceStepOverrideHelper.validateOverridesTypeAndUniquenessV2;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.ENVIRONMENT_GLOBAL_OVERRIDES;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.OVERRIDE_IN_REVERSE_PRIORITY;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_OVERRIDES;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_STEP_COMMAND_UNIT;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.execution.ServiceExecutionSummaryDetails;
import io.harness.cdng.execution.StageExecutionInfoUpdateDTO;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.mappers.ManifestSummaryMapper;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.steps.output.NgManifestsMetadataSweepingOutput;
import io.harness.cdng.manifest.steps.output.UnresolvedManifestsOutput;
import io.harness.cdng.manifest.steps.task.FetchManifestTaskContext;
import io.harness.cdng.manifest.steps.task.ManifestTaskService;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.kinds.HelmRepoOverrideManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.summary.ManifestSummary;
import io.harness.cdng.manifestConfigs.ManifestConfigurations;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorModule;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.delegate.beans.TaskData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator;
import io.harness.ng.core.service.mappers.ManifestFilterHelper;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.invokers.StrategyHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_K8S, HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ManifestsStepV2 implements SyncExecutable<EmptyStepParameters>, AsyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.MANIFESTS_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Named(ConnectorModule.DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Inject private NGSettingsClient ngSettingsClient;

  @Inject NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private StageExecutionInfoService stageExecutionInfoService;
  @Inject private ManifestTaskService manifestTaskService;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private StrategyHelper strategyHelper;

  private static final String OVERRIDE_PROJECT_SETTING_IDENTIFIER = "service_override_v2";

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, EmptyStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    final NGLogCallback logCallback =
        serviceStepsHelper.getServiceLogCallback(ambiance, false, SERVICE_STEP_COMMAND_UNIT);
    Optional<ManifestsOutcome> manifestsOutcome = resolveManifestsOutcome(ambiance, logCallback);

    List<String> callbackIds = new ArrayList<>();
    manifestsOutcome.ifPresent(manifests -> handleManifests(ambiance, manifests, callbackIds, logCallback));

    return AsyncExecutableResponse.newBuilder().addAllCallbackIds(callbackIds).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, EmptyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    if (isEmpty(responseDataMap)) {
      OptionalSweepingOutput manifestsOutcomeOutput = sweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.MANIFESTS));

      if (!manifestsOutcomeOutput.isFound()) {
        return StepResponse.builder().status(Status.SKIPPED).build();
      }
      saveManifestExecutionDataToStageInfo(ambiance, (ManifestsOutcome) manifestsOutcomeOutput.getOutput());
      return StepResponse.builder().status(Status.SUCCEEDED).build();
    }

    OptionalSweepingOutput unresolvedManifestsOutcomeOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.UNRESOLVED_MANIFESTS));

    if (!unresolvedManifestsOutcomeOutput.isFound()) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    UnresolvedManifestsOutput unresolvedManifestsOutput =
        (UnresolvedManifestsOutput) unresolvedManifestsOutcomeOutput.getOutput();

    ManifestsOutcome manifestsOutcome = unresolvedManifestsOutput.getManifestsOutcome();
    Map<String, String> taskIdMapping = unresolvedManifestsOutput.getTaskIdMapping();

    try {
      manifestTaskService.handleTaskResponses(responseDataMap, manifestsOutcome, taskIdMapping);
    } catch (Exception e) {
      return strategyHelper.handleException(e);
    }

    sweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.MANIFESTS, manifestsOutcome, StepCategory.STAGE.name());

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, EmptyStepParameters stepParameters, AsyncExecutableResponse executableResponse) {
    // nothing to do
  }

  @Override
  @Deprecated // Can be removed with next releases
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    final NGLogCallback logCallback =
        serviceStepsHelper.getServiceLogCallback(ambiance, false, SERVICE_STEP_COMMAND_UNIT);
    Optional<ManifestsOutcome> manifestsOutcome = resolveManifestsOutcome(ambiance, logCallback);

    manifestsOutcome.ifPresent(outcome -> saveManifestsOutcome(ambiance, outcome, new HashMap<>()));
    manifestsOutcome.ifPresent(outcome -> saveManifestExecutionDataToStageInfo(ambiance, outcome));

    return manifestsOutcome.map(ignored -> StepResponse.builder().status(Status.SUCCEEDED).build())
        .orElseGet(() -> StepResponse.builder().status(Status.SKIPPED).build());
  }

  private void handleManifests(
      Ambiance ambiance, ManifestsOutcome manifests, List<String> callbackIds, NGLogCallback logCallback) {
    Map<String, String> taskIdMapping = new HashMap<>();

    manifests.forEach((identifier, manifest) -> {
      final FetchManifestTaskContext context = FetchManifestTaskContext.builder()
                                                   .ambiance(ambiance)
                                                   .manifestOutcome(manifest)
                                                   .logCallback(logCallback)
                                                   .build();

      if (manifestTaskService.isSupported(context)) {
        Optional<TaskData> taskData = manifestTaskService.createTaskData(context);
        taskData.ifPresent(task -> {
          String taskId = queueTask(ambiance, task);
          logCallback.saveExecutionLog(
              LogHelper.color(String.format("Queued delegate task id: %s to fetch metadata for manifest: %s", taskId,
                                  manifest.getIdentifier()),
                  LogColor.Cyan, LogWeight.Bold));
          taskIdMapping.put(taskId, identifier);
          callbackIds.add(taskId);
        });
      }
    });

    saveManifestsOutcome(ambiance, manifests, taskIdMapping);
  }

  private String queueTask(Ambiance ambiance, TaskData taskData) {
    TaskRequest taskRequest =
        TaskRequestsUtils.prepareTaskRequestWithTaskSelector(ambiance, taskData, referenceFalseKryoSerializer,
            TaskCategory.DELEGATE_TASK_V2, emptyList(), false, taskData.getTaskType(), emptyList());

    DelegateTaskRequest delegateTaskRequest =
        cdStepHelper.mapTaskRequestToDelegateTaskRequest(taskRequest, taskData, emptySet(), "", true);

    return delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
  }

  private void saveManifestsOutcome(
      Ambiance ambiance, ManifestsOutcome manifestsOutcome, Map<String, String> taskIdMapping) {
    if (isEmpty(taskIdMapping)) {
      sweepingOutputService.consume(
          ambiance, OutcomeExpressionConstants.MANIFESTS, manifestsOutcome, StepCategory.STAGE.name());
    } else {
      UnresolvedManifestsOutput unresolvedManifestsOutput =
          UnresolvedManifestsOutput.builder().manifestsOutcome(manifestsOutcome).taskIdMapping(taskIdMapping).build();
      sweepingOutputService.consume(
          ambiance, OutcomeExpressionConstants.UNRESOLVED_MANIFESTS, unresolvedManifestsOutput, "");
    }
  }

  private Optional<ManifestsOutcome> resolveManifestsOutcome(Ambiance ambiance, NGLogCallback logCallback) {
    final NgManifestsMetadataSweepingOutput ngManifestsMetadataSweepingOutput =
        fetchManifestsMetadataFromSweepingOutput(ambiance);

    boolean isOverridesV2Enabled = isOverridesV2(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    final boolean isMultipleManifestEnabled = isMultipleManifestEnabled(
        AmbianceUtils.getAccountId(ambiance), ngManifestsMetadataSweepingOutput.getManifestConfigurations());

    List<ManifestConfigWrapper> manifests;
    Map<String, List<ManifestConfigWrapper>> finalSvcManifestsMapV1 = new HashMap<>();
    Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride = new HashMap<>();
    List<ManifestConfigWrapper> svcManifests = new ArrayList<>();
    String primaryManifestId = StringUtils.EMPTY;

    if (isMultipleManifestEnabled) {
      ParameterField<String> primaryManifestRef =
          ngManifestsMetadataSweepingOutput.getManifestConfigurations().getPrimaryManifestRef();
      resolve(ambiance, primaryManifestRef);
      primaryManifestId = getResolvedPrimaryManifestRef(primaryManifestRef);
    }

    if (isOverridesV2Enabled) {
      svcManifests = ngManifestsMetadataSweepingOutput.getSvcManifests();
      manifestsFromOverride = ngManifestsMetadataSweepingOutput.getManifestsFromOverride();

      if (isNoManifestConfiguredV2(svcManifests, manifestsFromOverride)) {
        logCallback.saveExecutionLog(
            "No manifests configured in the service. manifest expressions will not work", LogLevel.WARN);

        return Optional.empty();
      }
      svcManifests = filterServiceManifest(svcManifests, primaryManifestId, isMultipleManifestEnabled);
      manifests = aggregateManifestsFromAllLocationsV2(svcManifests, manifestsFromOverride, logCallback);

    } else {
      finalSvcManifestsMapV1 = ngManifestsMetadataSweepingOutput.getFinalSvcManifestsMap();

      if (noManifestsConfigured(finalSvcManifestsMapV1)) {
        logCallback.saveExecutionLog(
            "No manifests configured in the service. manifest expressions will not work", LogLevel.WARN);

        return Optional.empty();
      }
      finalSvcManifestsMapV1.replace(SERVICE,
          filterServiceManifest(finalSvcManifestsMapV1.get(SERVICE), primaryManifestId, isMultipleManifestEnabled));
      manifests = aggregateManifestsFromAllLocations(finalSvcManifestsMapV1);
    }

    List<ManifestAttributes> manifestAttributes = manifests.stream()
                                                      .map(ManifestConfigWrapper::getManifest)
                                                      .map(ManifestConfig::getSpec)
                                                      .collect(Collectors.toList());

    // rendering expressions with mode RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED to keep step group overrides as it is
    // these will be rendered by each step
    cdExpressionResolver.updateExpressions(ambiance, manifestAttributes);

    if (isOverridesV2Enabled) {
      validateOverridesTypeAndUniquenessV2(manifestsFromOverride, svcManifests);
    } else {
      validateOverridesTypeAndUniqueness(finalSvcManifestsMapV1,
          ngManifestsMetadataSweepingOutput.getServiceIdentifier(),
          ngManifestsMetadataSweepingOutput.getEnvironmentIdentifier());
    }

    manifestAttributes.forEach(manifestAttribute -> {
      Set<String> invalidParameters = manifestAttribute.validateAtRuntime();
      if (isNotEmpty(invalidParameters)) {
        logCallback.saveExecutionLog(
            String.format(
                "Values for following parameters for manifest %s are either empty or not provided: {%s}. This may result in failure of deployment.",
                manifestAttribute.getIdentifier(), invalidParameters.stream().collect(Collectors.joining(","))),
            LogLevel.WARN);
      }
    });

    SvcEnvV2ManifestValidator.validateManifestList(
        ngManifestsMetadataSweepingOutput.getServiceDefinitionType(), manifestAttributes);
    validateConnectors(ambiance, manifestAttributes);

    checkForAccessOrThrow(ambiance, manifestAttributes);

    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    for (int i = 0; i < manifestAttributes.size(); i++) {
      checkAndWarnIfDoesNotFollowIdentifierRegex(manifestAttributes.get(i).getIdentifier(), logCallback);
      ManifestOutcome manifestOutcome = ManifestOutcomeMapper.toManifestOutcome(manifestAttributes.get(i), i);
      manifestsOutcome.put(manifestOutcome.getIdentifier(), manifestOutcome);
    }

    return Optional.of(manifestsOutcome);
  }

  private static boolean isNoManifestConfiguredV2(List<ManifestConfigWrapper> svcManifests,
      Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride) {
    return isEmpty(svcManifests)
        && (isEmpty(manifestsFromOverride)
            || manifestsFromOverride.values().stream().noneMatch(EmptyPredicate::isNotEmpty));
  }

  // Used for override v2 design
  private List<ManifestConfigWrapper> aggregateManifestsFromAllLocationsV2(List<ManifestConfigWrapper> svcManifests,
      Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride, NGLogCallback logCallback) {
    List<ManifestConfigWrapper> manifests = new ArrayList<>();
    if (isNotEmpty(svcManifests)) {
      logCallback.saveExecutionLog("Adding manifest from service", LogLevel.INFO);
      manifests.addAll(svcManifests);
    }
    if (isNotEmpty(manifestsFromOverride)) {
      for (ServiceOverridesType overridesType : OVERRIDE_IN_REVERSE_PRIORITY) {
        if (manifestsFromOverride.containsKey(overridesType) && isNotEmpty(manifestsFromOverride.get(overridesType))) {
          logCallback.saveExecutionLog("Adding manifest from override type " + overridesType.toString(), LogLevel.INFO);
          manifests.addAll(manifestsFromOverride.get(overridesType));
        }
      }
    }

    overrideHelmRepoConnectorIfHelmChartExistsV2(manifestsFromOverride, manifests);
    return manifests;
  }

  private static boolean noManifestsConfigured(Map<String, List<ManifestConfigWrapper>> finalSvcManifestsMap) {
    return isEmpty(finalSvcManifestsMap)
        || finalSvcManifestsMap.values().stream().noneMatch(EmptyPredicate::isNotEmpty);
  }

  @NotNull
  private List<ManifestConfigWrapper> aggregateManifestsFromAllLocations(
      @NonNull Map<String, List<ManifestConfigWrapper>> finalSvcManifestsMap) {
    List<ManifestConfigWrapper> manifests = new ArrayList<>();
    if (isNotEmpty(finalSvcManifestsMap.get(SERVICE))) {
      manifests.addAll(finalSvcManifestsMap.get(SERVICE));
    }
    if (isNotEmpty(finalSvcManifestsMap.get(ENVIRONMENT_GLOBAL_OVERRIDES))) {
      manifests.addAll(finalSvcManifestsMap.get(ENVIRONMENT_GLOBAL_OVERRIDES));
    }
    if (isNotEmpty(finalSvcManifestsMap.get(SERVICE_OVERRIDES))) {
      manifests.addAll(finalSvcManifestsMap.get(SERVICE_OVERRIDES));
    }

    overrideHelmRepoConnectorIfHelmChartExists(finalSvcManifestsMap, manifests);
    return manifests;
  }

  private void overrideHelmRepoConnectorIfHelmChartExists(
      Map<String, List<ManifestConfigWrapper>> finalSvcManifestsMap, List<ManifestConfigWrapper> manifests) {
    Optional<ManifestConfigWrapper> helmChartOptional =
        manifests.stream()
            .filter(manifest -> ManifestConfigType.HELM_CHART == manifest.getManifest().getType())
            .findFirst();
    helmChartOptional.ifPresent((ManifestConfigWrapper manifestConfigWrapper)
                                    -> overrideHelmRepoConnector(manifestConfigWrapper, finalSvcManifestsMap));
    manifests.removeIf(manifest -> ManifestConfigType.HELM_REPO_OVERRIDE == manifest.getManifest().getType());
  }

  // Used for override v2 design
  private void overrideHelmRepoConnectorIfHelmChartExistsV2(
      Map<ServiceOverridesType, List<ManifestConfigWrapper>> finalSvcManifestsMap,
      List<ManifestConfigWrapper> manifests) {
    Optional<ManifestConfigWrapper> helmChartOptional =
        manifests.stream()
            .filter(manifest -> ManifestConfigType.HELM_CHART == manifest.getManifest().getType())
            .findFirst();
    helmChartOptional.ifPresent((ManifestConfigWrapper manifestConfigWrapper)
                                    -> overrideHelmRepoConnectorV2(manifestConfigWrapper, finalSvcManifestsMap));
    manifests.removeIf(manifest -> ManifestConfigType.HELM_REPO_OVERRIDE == manifest.getManifest().getType());
  }

  private void overrideHelmRepoConnector(
      ManifestConfigWrapper svcHelmChart, Map<String, List<ManifestConfigWrapper>> finalSvcManifestsMap) {
    useHelmRepoOverrideIfExists(ENVIRONMENT_GLOBAL_OVERRIDES, svcHelmChart, finalSvcManifestsMap);
    useHelmRepoOverrideIfExists(SERVICE_OVERRIDES, svcHelmChart, finalSvcManifestsMap);
  }

  // Used for override v2 design
  private void overrideHelmRepoConnectorV2(
      ManifestConfigWrapper svcHelmChart, Map<ServiceOverridesType, List<ManifestConfigWrapper>> finalSvcManifestsMap) {
    for (ServiceOverridesType overridesType : OVERRIDE_IN_REVERSE_PRIORITY) {
      useHelmRepoOverrideIfExistsV2(overridesType, svcHelmChart, finalSvcManifestsMap);
    }
  }

  private void useHelmRepoOverrideIfExists(String overrideType, ManifestConfigWrapper svcHelmChart,
      Map<String, List<ManifestConfigWrapper>> finalSvcManifestsMap) {
    List<ManifestConfigWrapper> overrides = finalSvcManifestsMap.get(overrideType);

    if (isNotEmpty(overrides)) {
      List<ManifestConfigWrapper> helmOverrides = extractHelmRepoOverrides(overrides);
      if (!helmOverrides.isEmpty()) {
        useHelmRepoOverride(svcHelmChart, helmOverrides.get(0));
      }
    }
  }

  private void useHelmRepoOverrideIfExistsV2(ServiceOverridesType overrideType, ManifestConfigWrapper svcHelmChart,
      Map<ServiceOverridesType, List<ManifestConfigWrapper>> finalSvcManifestsMap) {
    List<ManifestConfigWrapper> overrides = finalSvcManifestsMap.get(overrideType);

    if (isNotEmpty(overrides)) {
      List<ManifestConfigWrapper> helmOverrides = extractHelmRepoOverrides(overrides);
      if (!helmOverrides.isEmpty()) {
        useHelmRepoOverride(svcHelmChart, helmOverrides.get(0));
      }
    }
  }

  private List<ManifestConfigWrapper> extractHelmRepoOverrides(List<ManifestConfigWrapper> overrides) {
    return overrides.stream()
        .filter(manifest -> ManifestConfigType.HELM_REPO_OVERRIDE == manifest.getManifest().getType())
        .collect(Collectors.toList());
  }

  private void useHelmRepoOverride(ManifestConfigWrapper helmChart, ManifestConfigWrapper repoOverride) {
    HelmChartManifest helmChartManifest = (HelmChartManifest) helmChart.getManifest().getSpec();
    HelmRepoOverrideManifest helmRepoOverride = (HelmRepoOverrideManifest) repoOverride.getManifest().getSpec();
    if (helmChartManifest != null && helmRepoOverride != null) {
      SvcEnvV2ManifestValidator.validateHelmRepoOverrideContainsSameManifestType(helmChartManifest, helmRepoOverride);
      StoreConfig helmChartManifestStoreConfig = helmChartManifest.getStoreConfig();
      if (helmChartManifestStoreConfig != null) {
        helmChartManifestStoreConfig.overrideConnectorRef(helmRepoOverride.getConnectorRef());
      }
    }
  }

  private NgManifestsMetadataSweepingOutput fetchManifestsMetadataFromSweepingOutput(Ambiance ambiance) {
    final OptionalSweepingOutput resolveOptional = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT));
    if (!resolveOptional.isFound()) {
      log.info("Could not find manifestFilesSweepingOutput for the stage.");
    }
    return resolveOptional.isFound() ? (NgManifestsMetadataSweepingOutput) resolveOptional.getOutput()
                                     : NgManifestsMetadataSweepingOutput.builder().build();
  }

  private void validateConnectors(Ambiance ambiance, List<ManifestAttributes> manifestAttributes) {
    // In some cases (eg. in k8s manifests) we're skipping auto evaluation, in this case we can skip connector
    // validation for now. It will be done when all expression will be resolved
    final List<ManifestAttributes> manifestsToConsider =
        manifestAttributes.stream()
            .filter(m -> m.getStoreConfig().getConnectorReference() != null)
            .filter(m -> !m.getStoreConfig().getConnectorReference().isExpression())
            .collect(Collectors.toList());

    final List<ManifestAttributes> missingConnectorManifests =
        manifestsToConsider.stream()
            .filter(m -> ParameterField.isNull(m.getStoreConfig().getConnectorReference()))
            .collect(Collectors.toList());
    if (EmptyPredicate.isNotEmpty(missingConnectorManifests)) {
      throw new InvalidRequestException("Connector ref field not present in manifests with identifiers "
          + missingConnectorManifests.stream().map(ManifestAttributes::getIdentifier).collect(Collectors.joining(",")));
    }

    final Set<String> connectorIdentifierRefs = manifestsToConsider.stream()
                                                    .map(ManifestAttributes::getStoreConfig)
                                                    .map(StoreConfig::getConnectorReference)
                                                    .map(ParameterField::getValue)
                                                    .collect(Collectors.toSet());

    final Set<String> connectorsNotFound = new HashSet<>();
    final Set<String> connectorsNotValid = new HashSet<>();
    final NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    for (String connectorIdentifierRef : connectorIdentifierRefs) {
      IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifierRef,
          ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
      Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
          connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
      if (connectorDTO.isEmpty()) {
        connectorsNotFound.add(connectorIdentifierRef);
        continue;
      }
      if (!ConnectorUtils.isValid(connectorDTO.get())) {
        connectorsNotValid.add(connectorIdentifierRef);
      }
    }
    if (isNotEmpty(connectorsNotFound)) {
      throw new InvalidRequestException(
          String.format("Connectors with identifier(s) [%s] not found", String.join(",", connectorsNotFound)));
    }

    if (isNotEmpty(connectorsNotValid)) {
      throw new InvalidRequestException(
          format("Connectors with identifiers [%s] is(are) invalid. Please fix the connector YAMLs.",
              String.join(",", connectorsNotValid)));
    }
  }

  void checkForAccessOrThrow(Ambiance ambiance, List<ManifestAttributes> manifestAttributes) {
    if (EmptyPredicate.isEmpty(manifestAttributes)) {
      return;
    }
    List<EntityDetail> entityDetails = new ArrayList<>();

    for (ManifestAttributes manifestAttribute : manifestAttributes) {
      Set<EntityDetailProtoDTO> entityDetailsProto = manifestAttribute == null
          ? Set.of()
          : entityReferenceExtractorUtils.extractReferredEntities(ambiance, manifestAttribute);

      List<EntityDetail> entityDetail =
          entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(entityDetailsProto)));

      if (EmptyPredicate.isNotEmpty(entityDetail)) {
        entityDetails.addAll(entityDetail);
      }
    }
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  private void checkAndWarnIfDoesNotFollowIdentifierRegex(String str, NGLogCallback logCallback) {
    if (isNotEmpty(str)) {
      final Pattern identifierPattern = EntityIdentifierValidator.IDENTIFIER_PATTERN;
      if (!identifierPattern.matcher(str).matches()) {
        logCallback.saveExecutionLog(
            LogHelper.color(
                String.format(
                    "Manifest identifier [%s] is not valid as per Harness Identifier Regex %s. Using this identifier in harness expressions might not work",
                    str, identifierPattern.pattern()),
                LogColor.Yellow, LogWeight.Bold),
            LogLevel.WARN);
      }
    }
  }

  private boolean isOverridesV2(String accountId, String orgId, String projectId) {
    return featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_SERVICE_OVERRIDES_2_0)
        && NGRestUtils
               .getResponse(
                   ngSettingsClient.getSetting(OVERRIDE_PROJECT_SETTING_IDENTIFIER, accountId, orgId, projectId))
               .getValue()
               .equals("true");
  }

  public void saveManifestExecutionDataToStageInfo(Ambiance ambiance, ManifestsOutcome manifestsOutcome) {
    if (isNull(manifestsOutcome)) {
      return;
    }
    if (!featureFlagHelperService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_CUSTOM_STAGE_EXECUTION_DATA_SYNC)) {
      return;
    }
    try {
      List<ManifestSummary> manifestsSummary = mapManifestOutcomeToSummary(manifestsOutcome);
      if (isNotEmpty(manifestsSummary)) {
        stageExecutionInfoService.updateStageExecutionInfo(ambiance,
            StageExecutionInfoUpdateDTO.builder()
                .manifestsSummary(ServiceExecutionSummaryDetails.ManifestsSummary.builder()
                                      .manifestSummaries(manifestsSummary)
                                      .build())
                .build());
      }
    } catch (Exception e) {
      log.error(String.format(
          "[CustomDashboard]: Error while saving manifest info to StageExecutionInfo for accountIdentifier %s, orgIdentifier %s, projectIdentifier %s and stageExecutionId %s",
          AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), ambiance.getStageExecutionId()));
    }
  }

  private List<ManifestSummary> mapManifestOutcomeToSummary(ManifestsOutcome manifestsOutcome) {
    List<ManifestSummary> manifestSummaries = new ArrayList<>();
    Collection<ManifestOutcome> manifestOutcomeList = manifestsOutcome.values();
    for (ManifestOutcome manifestOutcome : manifestOutcomeList) {
      ManifestSummary manifestSummary = ManifestSummaryMapper.toManifestSummary(manifestOutcome);
      if (manifestSummary != null) {
        manifestSummaries.add(manifestSummary);
      }
    }
    return manifestSummaries;
  }

  private boolean isMultipleManifestEnabled(String accountId, ManifestConfigurations manifestConfigurations) {
    return ManifestFilterHelper.hasPrimaryManifestRef(manifestConfigurations)
        && featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG);
  }

  private void resolve(Ambiance ambiance, Object... objects) {
    final List<Object> toResolve = new ArrayList<>(Arrays.asList(objects));
    cdExpressionResolver.updateExpressions(ambiance, toResolve);
  }

  private String getResolvedPrimaryManifestRef(ParameterField<String> primaryManifestRef) {
    String primaryManifestId = ParameterFieldHelper.getParameterFieldValue(primaryManifestRef);
    if (isEmpty(primaryManifestId)) {
      throw new InvalidRequestException(
          String.format("Unable to resolve primaryManifestRef. Please check the expression %s",
              primaryManifestRef.isExpression() ? primaryManifestRef.getExpressionValue() : StringUtils.EMPTY));
    }
    return primaryManifestId;
  }

  private List<ManifestConfigWrapper> filterServiceManifest(
      List<ManifestConfigWrapper> svcManifests, String primaryManifestId, boolean isMultipleManifestEnabled) {
    if (!isMultipleManifestEnabled) {
      return svcManifests;
    }
    List<ManifestConfigWrapper> updatedSvcManifests =
        svcManifests.stream()
            .filter(manifest
                -> !MULTIPLE_SUPPORTED_MANIFEST_TYPES.contains(manifest.getManifest().getType().getDisplayName())
                    || manifest.getManifest().getIdentifier().equals(primaryManifestId))
            .collect(Collectors.toList());

    if (updatedSvcManifests.stream().noneMatch(manifest
            -> MULTIPLE_SUPPORTED_MANIFEST_TYPES.contains(manifest.getManifest().getType().getDisplayName()))) {
      throw new InvalidRequestException(String.format("primaryManifestRef: %s does not match to any [%S] manifests",
          primaryManifestId, String.join(", ", MULTIPLE_SUPPORTED_MANIFEST_TYPES)));
    }
    return updatedSvcManifests;
  }
}
