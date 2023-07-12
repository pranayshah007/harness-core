/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.execution.ServiceExecutionSummaryDetails;
import io.harness.cdng.execution.StageExecutionInfoUpdateDTO;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.mappers.ManifestSummaryMapper;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.summary.ManifestSummary;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedTypeException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class ManifestStep implements SyncExecutable<ManifestStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.MANIFEST.getName()).setStepCategory(StepCategory.STEP).build();

  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private StageExecutionInfoService stageExecutionInfoService;

  @Override
  public Class<ManifestStepParameters> getStepParametersClass() {
    return ManifestStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ManifestStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    // NOTE(sahil): Commented these log lines as we are not doing anything and
    // they were causing confusion with artifact step logs as they were running in parallel.
    // NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    // logCallback.saveExecutionLog(format("Processing manifest [%s]...", stepParameters.getIdentifier()));
    ManifestAttributes finalManifest = applyManifestsOverlay(stepParameters);
    getConnector(finalManifest, ambiance);
    // logCallback.saveExecutionLog(format("Processed manifest [%s]", stepParameters.getIdentifier()));
    ManifestOutcome manifestOutcome = ManifestOutcomeMapper.toManifestOutcome(finalManifest, stepParameters.getOrder());
    saveManifestExecutionDataToStageInfo(ambiance, manifestOutcome);
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepOutcome.builder().name("output").outcome(manifestOutcome).build())
        .build();
  }

  private ManifestAttributes applyManifestsOverlay(ManifestStepParameters stepParameters) {
    List<ManifestAttributes> manifestList = new LinkedList<>();
    // 1. Original manifests
    if (stepParameters.getSpec() != null) {
      manifestList.add(stepParameters.getSpec());
    }
    // 2. Stage Overrides
    if (stepParameters.getStageOverride() != null) {
      manifestList.add(stepParameters.getStageOverride());
    }
    if (EmptyPredicate.isEmpty(manifestList)) {
      throw new InvalidArgumentsException("No manifests defined");
    }
    ManifestAttributes resultantManifest = manifestList.get(0);
    for (ManifestAttributes manifest : manifestList.subList(1, manifestList.size())) {
      if (!manifest.getKind().equals(resultantManifest.getKind())) {
        throw new UnexpectedTypeException(
            format("Unable to apply manifest override of type '%s' to manifest of type '%s' with identifier '%s'",
                manifest.getKind(), resultantManifest.getKind(), resultantManifest.getIdentifier()));
      }

      resultantManifest = resultantManifest.applyOverrides(manifest);
    }
    return resultantManifest;
  }

  private void getConnector(ManifestAttributes manifestAttributes, Ambiance ambiance) {
    // In some cases (eg. in k8s manifests) we're skipping auto evaluation, in this case we can skip connector
    // validation for now. It will be done when all expression will be resolved
    if (isNull(manifestAttributes.getStoreConfig().getConnectorReference())) {
      return;
    }
    if (manifestAttributes.getStoreConfig().getConnectorReference().isExpression()) {
      return;
    }

    if (ParameterField.isNull(manifestAttributes.getStoreConfig().getConnectorReference())) {
      throw new InvalidRequestException(
          "Connector ref field not present in manifest with identifier " + manifestAttributes.getIdentifier());
    }
    String connectorIdentifierRef = manifestAttributes.getStoreConfig().getConnectorReference().getValue();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifierRef,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(
          String.format("Connector not found for identifier : [%s]", connectorIdentifierRef));
    }
    ConnectorUtils.checkForConnectorValidityOrThrow(connectorDTO.get());
  }

  public void saveManifestExecutionDataToStageInfo(Ambiance ambiance, ManifestOutcome manifestOutcome) {
    if (isNull(manifestOutcome)) {
      return;
    }
    if (!featureFlagHelperService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_CUSTOM_STAGE_EXECUTION_DATA_SYNC)) {
      return;
    }
    try {
      List<ManifestSummary> manifestsSummary = mapManifestOutcomeToSummary(manifestOutcome);
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

  private List<ManifestSummary> mapManifestOutcomeToSummary(ManifestOutcome manifestOutcome) {
    List<ManifestSummary> manifestSummaries = new ArrayList<>();
    ManifestSummary manifestSummary = ManifestSummaryMapper.toManifestSummary(manifestOutcome);
    if (manifestSummary != null) {
      manifestSummaries.add(manifestSummary);
    }
    return manifestSummaries;
  }
}
