/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps;

import static io.harness.cdng.service.steps.ServiceStepOverrideHelper.validateOverridesTypeAndUniqueness;
import static io.harness.cdng.service.steps.ServiceStepOverrideHelper.validateOverridesTypeAndUniquenessV2;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.ENVIRONMENT_GLOBAL_OVERRIDES;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.OVERRIDE_IN_REVERSE_PRIORITY;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_OVERRIDES;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.steps.output.NgManifestsMetadataSweepingOutput;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.kinds.HelmRepoOverrideManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorModule;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.validator.EntityIdentifierValidator;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
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
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ManifestsStepV2 implements SyncExecutable<EmptyStepParameters> {
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

  private static final String OVERRIDE_PROJECT_SETTING_IDENTIFIER = "service_override_v2";

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    final NgManifestsMetadataSweepingOutput ngManifestsMetadataSweepingOutput =
        fetchManifestsMetadataFromSweepingOutput(ambiance);

    boolean isOverridesV2Enabled = isOverridesV2(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    List<ManifestConfigWrapper> manifests = new ArrayList<>();
    Map<String, List<ManifestConfigWrapper>> finalSvcManifestsMapV1 = new HashMap<>();
    Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride = new HashMap<>();
    List<ManifestConfigWrapper> svcManifests = new ArrayList<>();
    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);

    if (isOverridesV2Enabled) {
      svcManifests = ngManifestsMetadataSweepingOutput.getSvcManifests();
      manifestsFromOverride = ngManifestsMetadataSweepingOutput.getManifestsFromOverride();

      if (isNoManifestConfiguredV2(svcManifests, manifestsFromOverride)) {
        logCallback.saveExecutionLog(
            "No manifests configured in the service. manifest expressions will not work", LogLevel.WARN);

        return StepResponse.builder().status(Status.SKIPPED).build();
      }
      manifests = aggregateManifestsFromAllLocationsV2(svcManifests, manifestsFromOverride, logCallback);

    } else {
      finalSvcManifestsMapV1 = ngManifestsMetadataSweepingOutput.getFinalSvcManifestsMap();

      if (noManifestsConfigured(finalSvcManifestsMapV1)) {
        logCallback.saveExecutionLog(
            "No manifests configured in the service. manifest expressions will not work", LogLevel.WARN);

        return StepResponse.builder().status(Status.SKIPPED).build();
      }
      manifests = aggregateManifestsFromAllLocations(finalSvcManifestsMapV1);
    }

    List<ManifestAttributes> manifestAttributes = manifests.stream()
                                                      .map(ManifestConfigWrapper::getManifest)
                                                      .map(ManifestConfig::getSpec)
                                                      .collect(Collectors.toList());
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

    sweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.MANIFESTS, manifestsOutcome, StepCategory.STAGE.name());

    return StepResponse.builder().status(Status.SUCCEEDED).build();
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
}
