/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps;

import static io.harness.cdng.manifest.ManifestType.HELM_SUPPORTED_MANIFEST_TYPES;
import static io.harness.cdng.manifest.ManifestType.K8S_SUPPORTED_MANIFEST_TYPES;
import static io.harness.cdng.service.steps.ServiceStepOverrideHelper.ENVIRONMENT_GLOBAL_OVERRIDES;
import static io.harness.cdng.service.steps.ServiceStepOverrideHelper.SERVICE;
import static io.harness.cdng.service.steps.ServiceStepOverrideHelper.SERVICE_OVERRIDES;
import static io.harness.cdng.service.steps.ServiceStepOverrideHelper.validateOverridesTypeAndUniqueness;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorModule;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    final NgManifestsMetadataSweepingOutput ngManifestsMetadataSweepingOutput =
        fetchManifestsMetadataFromSweepingOutput(ambiance);

    final Map<String, List<ManifestConfigWrapper>> finalSvcManifestsMap =
        ngManifestsMetadataSweepingOutput.getFinalSvcManifestsMap();

    if (isEmpty(finalSvcManifestsMap)) {
      log.info("no manifest files found for service " + ngManifestsMetadataSweepingOutput.getServiceIdentifier()
          + ". skipping the manifest files step");
      return StepResponse.builder().status(Status.SKIPPED).build();
    }
    List<ManifestConfigWrapper> manifests = aggregateManifestsFromAllLocations(finalSvcManifestsMap);
    List<ManifestAttributes> manifestAttributes = manifests.stream()
                                                      .map(ManifestConfigWrapper::getManifest)
                                                      .map(ManifestConfig::getSpec)
                                                      .collect(Collectors.toList());
    cdExpressionResolver.updateExpressions(ambiance, manifestAttributes);
    validateOverridesTypeAndUniqueness(finalSvcManifestsMap, ngManifestsMetadataSweepingOutput.getServiceIdentifier(),
        ngManifestsMetadataSweepingOutput.getEnvironmentIdentifier());

    validateManifestList(ngManifestsMetadataSweepingOutput.getServiceDefinitionType(), manifestAttributes);
    validateConnectors(ambiance, manifestAttributes);

    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    for (int i = 0; i < manifestAttributes.size(); i++) {
      ManifestOutcome manifestOutcome = ManifestOutcomeMapper.toManifestOutcome(manifestAttributes.get(i), i);
      manifestsOutcome.put(manifestOutcome.getIdentifier(), manifestOutcome);
    }

    sweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.MANIFESTS, manifestsOutcome, StepCategory.STAGE.name());

    return StepResponse.builder().status(Status.SUCCEEDED).build();
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
    return manifests;
  }

  private NgManifestsMetadataSweepingOutput fetchManifestsMetadataFromSweepingOutput(Ambiance ambiance) {
    final OptionalSweepingOutput resolveOptional = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_MANIFESTS_SWEEPING_OUTPUT));
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

  private void validateManifestList(
      ServiceDefinitionType serviceDefinitionType, List<ManifestAttributes> manifestList) {
    if (serviceDefinitionType == null) {
      return;
    }

    switch (serviceDefinitionType) {
      case KUBERNETES:
        validateDuplicateManifests(
            manifestList, K8S_SUPPORTED_MANIFEST_TYPES, ServiceDefinitionType.KUBERNETES.getYamlName());
        break;
      case NATIVE_HELM:
        validateDuplicateManifests(
            manifestList, HELM_SUPPORTED_MANIFEST_TYPES, ServiceDefinitionType.NATIVE_HELM.getYamlName());
        break;
      default:
    }
  }

  private void validateDuplicateManifests(
      List<ManifestAttributes> manifestList, Set<String> supported, String deploymentType) {
    final Map<String, String> manifestIdTypeMap =
        manifestList.stream()
            .filter(m -> supported.contains(m.getKind()))
            .collect(Collectors.toMap(ManifestAttributes::getIdentifier, ManifestAttributes::getKind));

    if (manifestIdTypeMap.values().size() > 1) {
      String manifestIdType = manifestIdTypeMap.entrySet()
                                  .stream()
                                  .map(entry -> String.format("%s : %s", entry.getKey(), entry.getValue()))
                                  .collect(Collectors.joining(", "));
      throw new InvalidRequestException(String.format(
          "Multiple manifests found [%s]. %s deployment support only one manifest of one of types: %s. Remove all unused manifests",
          manifestIdType, deploymentType, String.join(", ", supported)));
    }
  }
}
