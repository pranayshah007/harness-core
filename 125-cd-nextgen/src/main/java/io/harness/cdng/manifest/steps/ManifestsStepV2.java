package io.harness.cdng.manifest.steps;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.service.steps.ServiceSweepingOutput;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ManifestsStepV2 implements SyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.MANIFESTS_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    ServiceSweepingOutput serviceSweepingOutput = (ServiceSweepingOutput) sweepingOutputService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT));
    NGServiceConfig ngServiceConfig = null;
    if (serviceSweepingOutput != null) {
      try {
        ngServiceConfig = YamlUtils.read(serviceSweepingOutput.getFinalServiceYaml(), NGServiceConfig.class);
      } catch (IOException e) {
        // Todo:(yogesh) handle exception
        throw new RuntimeException(e);
      }
    }

    if (ngServiceConfig == null || ngServiceConfig.getNgServiceV2InfoConfig() == null) {
      log.info("No service configuration found");
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    final List<ManifestConfigWrapper> manifests =
        ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getManifests();

    if (isEmpty(manifests)) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    List<ManifestAttributes> manifestAttributes = manifests.stream()
                                                      .map(ManifestConfigWrapper::getManifest)
                                                      .map(ManifestConfig::getSpec)
                                                      .collect(Collectors.toList());

    validateConnectors(ambiance, manifestAttributes);

    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    for (int i = 0; i < manifestAttributes.size(); i++) {
      ManifestOutcome manifestOutcome = ManifestOutcomeMapper.toManifestOutcome(manifestAttributes.get(i), i);
      manifestsOutcome.put(manifestOutcome.getIdentifier(), manifestOutcome);
    }

    sweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.MANIFESTS, manifestsOutcome, StepCategory.STAGE.name());

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .build();
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
}
