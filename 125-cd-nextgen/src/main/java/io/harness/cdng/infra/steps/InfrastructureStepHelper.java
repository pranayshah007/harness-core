/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.logging.LogCallbackUtils.saveExecutionLogSafely;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.execution.InfraExecutionSummaryDetails;
import io.harness.cdng.execution.InfraExecutionSummaryDetails.InfraExecutionSummaryDetailsBuilder;
import io.harness.cdng.execution.StageExecutionInfoUpdateDTO;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureDetailsAbstract;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.ConnectorConnectivityDetails;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class InfrastructureStepHelper {
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private StageExecutionHelper stageExecutionHelper;
  @Inject private StageExecutionInfoService stageExecutionInfoService;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  public NGLogCallback getInfrastructureLogCallback(Ambiance ambiance) {
    return getInfrastructureLogCallback(ambiance, false);
  }

  public NGLogCallback getInfrastructureLogCallback(Ambiance ambiance, String logSuffix) {
    return getInfrastructureLogCallback(ambiance, false, logSuffix);
  }

  public NGLogCallback getInfrastructureLogCallback(Ambiance ambiance, boolean shouldOpenStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, null, shouldOpenStream);
  }

  public NGLogCallback getInfrastructureLogCallback(Ambiance ambiance, boolean shouldOpenStream, String logSuffix) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, logSuffix, shouldOpenStream);
  }
  public List<ConnectorInfoDTO> validateAndGetConnectors(
      List<ParameterField<String>> connectorRefs, Ambiance ambiance, NGLogCallback logCallback) {
    return connectorRefs.stream()
        .map(connectorRef -> validateAndGetConnector(connectorRef, ambiance, logCallback))
        .collect(Collectors.toList());
  }

  public ConnectorInfoDTO validateAndGetConnector(
      ParameterField<String> connectorRef, Ambiance ambiance, NGLogCallback logCallback) {
    saveExecutionLogSafely(logCallback, "Fetching and validating connector...");

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    if (ParameterField.isNull(connectorRef)) {
      throw new InvalidRequestException("Connector ref field not present in infrastructure");
    }
    if (connectorRef.isExpression()) {
      if (NGExpressionUtils.isRuntimeField(connectorRef.getExpressionValue())) {
        throw new InvalidRequestException(
            "Connector ref is a runtime input but its value is not provided in the infrastructure");
      }
      throw new InvalidRequestException(String.format(
          "Connector ref [%s] could not be resolved in infrastructure", connectorRef.getExpressionValue()));
    }
    String connectorRefValue = connectorRef.getValue();
    IdentifierRef connectorIdentifierRef = IdentifierRefHelper.getIdentifierRef(connectorRefValue,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(connectorIdentifierRef.getAccountIdentifier(), connectorIdentifierRef.getOrgIdentifier(),
            connectorIdentifierRef.getProjectIdentifier(), connectorIdentifierRef.getIdentifier());
    ConnectorInfoDTO connectorInfoDTO;
    if (connectorDTO.isEmpty()) {
      throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorRefValue));
    } else {
      saveExecutionLogSafely(logCallback, color("Connector fetched", Green));

      connectorInfoDTO = connectorDTO.get().getConnector();
      if (connectorInfoDTO != null) {
        if (EmptyPredicate.isNotEmpty(connectorInfoDTO.getName())) {
          saveExecutionLogSafely(logCallback, color(format("Connector Name: %s", connectorInfoDTO.getName()), Yellow));
        }

        if (connectorInfoDTO.getConnectorType() != null
            && EmptyPredicate.isNotEmpty(connectorInfoDTO.getConnectorType().name())) {
          saveExecutionLogSafely(
              logCallback, color(format("Connector Type: %s", connectorInfoDTO.getConnectorType().name()), Yellow));
        }
      }

      ConnectorConnectivityDetails connectorConnectivityDetails = connectorDTO.get().getStatus();
      if (connectorConnectivityDetails != null && connectorConnectivityDetails.getStatus() != null
          && EmptyPredicate.isNotEmpty(connectorConnectivityDetails.getStatus().name())) {
        saveExecutionLogSafely(logCallback,
            color(format("Connector Status: %s", connectorConnectivityDetails.getStatus().name()), Yellow));
      }
    }
    ConnectorUtils.checkForConnectorValidityOrThrow(connectorDTO.get());

    return connectorInfoDTO;
  }

  @SafeVarargs
  public final <T> void validateExpression(ParameterField<T>... inputs) {
    for (ParameterField<T> input : inputs) {
      if (unresolvedExpression(input)) {
        throw new InvalidRequestException(format("Unresolved Expression : [%s]", input.getExpressionValue()));
      }
    }
  }

  private <T> boolean unresolvedExpression(ParameterField<T> input) {
    return !ParameterField.isNull(input) && input.isExpression();
  }

  public void requireOne(ParameterField<?> first, ParameterField<?> second) {
    if (unresolvedExpression(first) && unresolvedExpression(second)) {
      throw new InvalidRequestException(
          format("Unresolved Expressions : [%s] , [%s]", first.getExpressionValue(), second.getExpressionValue()));
    }
  }

  public boolean getSkipInstances(Infrastructure infrastructure) {
    boolean skipInstances = false;
    if (stageExecutionHelper.isSshWinRmInfrastructureKind(infrastructure.getKind())
        && (((InfrastructureDetailsAbstract) infrastructure).getSkipInstances() != null)) {
      skipInstances = ((InfrastructureDetailsAbstract) infrastructure).getSkipInstances();
    }
    return skipInstances;
  }

  public void saveInfraExecutionDataToStageInfo(Ambiance ambiance, StepResponse stepResponse) {
    stageExecutionInfoService.updateStageExecutionInfo(ambiance,
        StageExecutionInfoUpdateDTO.builder()
            .infraExecutionSummary(createInfraExecutionSummaryDetailsFromStepResponse(stepResponse))
            .build());
  }

  public InfraExecutionSummaryDetails createInfraExecutionSummaryDetailsFromStepResponse(StepResponse stepResponse) {
    if (stepResponse.getStepOutcomes() != null) {
      for (StepResponse.StepOutcome stepOutcome : stepResponse.getStepOutcomes()) {
        if (stepOutcome.getOutcome() instanceof InfrastructureOutcome) {
          InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) stepOutcome.getOutcome();
          InfraExecutionSummaryDetailsBuilder infraExecutionSummaryDetailsBuilder =
              InfraExecutionSummaryDetails.builder()
                  .infrastructureIdentifier(infrastructureOutcome.getInfraIdentifier())
                  .infrastructureName(infrastructureOutcome.getInfraName())
                  .connectorRef(infrastructureOutcome.getConnectorRef());
          if (infrastructureOutcome.getEnvironment() != null) {
            infraExecutionSummaryDetailsBuilder.identifier(infrastructureOutcome.getEnvironment().getIdentifier())
                .name(infrastructureOutcome.getEnvironment().getName())
                .type(infrastructureOutcome.getEnvironment().getType().name())
                .envGroupId(infrastructureOutcome.getEnvironment().getEnvGroupRef())
                .envGroupName(infrastructureOutcome.getEnvironment().getEnvGroupName());
          }
          return infraExecutionSummaryDetailsBuilder.build();
        }
      }
    }
    return null;
  }
}
