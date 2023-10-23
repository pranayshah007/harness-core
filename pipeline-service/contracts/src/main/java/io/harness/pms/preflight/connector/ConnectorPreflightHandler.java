/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.connector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorConnectivityDetails;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.preflight.PreFlightCause;
import io.harness.pms.preflight.PreFlightEntityErrorInfo;
import io.harness.pms.preflight.PreFlightResolution;
import io.harness.pms.preflight.PreFlightStatus;
import io.harness.pms.preflight.PreflightCommonUtils;
import io.harness.pms.preflight.connector.ConnectorCheckResponse.ConnectorCheckResponseBuilder;
import io.harness.pms.yaml.YamlUtils;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.remote.client.NGRestUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class ConnectorPreflightHandler {
  private static final int PAGE = 0;
  private static final int SIZE = 100;

  @Inject private ConnectorResourceClient connectorResourceClient;

  public List<ConnectorCheckResponse> getConnectorCheckResponseTemplate(List<EntityDetail> entityDetails) {
    return entityDetails.stream()
        .map(entityDetail
            -> ConnectorCheckResponse.builder()
                   .connectorIdentifier(entityDetail.getEntityRef().getIdentifier())
                   .status(PreFlightStatus.UNKNOWN)
                   .build())
        .collect(Collectors.toList());
  }

  public List<ConnectorCheckResponse> getConnectorCheckResponsesForReferredConnectors(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, Map<String, Object> fqnToObjectMapMergedYaml,
      List<EntityDetail> connectorUsages) {
    Map<Scope, List<String>> scopeToConnectorIdentifiers = new HashMap<>();
    Map<String, String> connectorIdentifierToFqn = new HashMap<>();
    for (EntityDetail connector : connectorUsages) {
      IdentifierRef ref = (IdentifierRef) connector.getEntityRef();
      Scope refScope = ref.getScope();
      String refIdentifier = ref.getIdentifier();
      Map<String, String> metadata = ref.getMetadata();
      String fqn = metadata.get(PreFlightCheckMetadata.FQN);
      connectorIdentifierToFqn.put(refIdentifier, fqn);

      if (refScope == Scope.UNKNOWN) {
        throw new InvalidRequestException("UNKNOWN scope not supported");
      }
      List<String> existingIdentifiers = scopeToConnectorIdentifiers.getOrDefault(refScope, Lists.newArrayList());
      existingIdentifiers.add(refIdentifier);
      scopeToConnectorIdentifiers.put(refScope, existingIdentifiers);
    }
    List<ConnectorResponseDTO> connectorResponsesDTO =
        getConnectorResponses(accountIdentifier, orgIdentifier, projectIdentifier, scopeToConnectorIdentifiers);

    List<ConnectorResponseDTO> finalResponse = filterConnectorResponse(connectorResponsesDTO, connectorUsages);

    return getConnectorCheckResponse(fqnToObjectMapMergedYaml, finalResponse, connectorIdentifierToFqn);
  }

  @VisibleForTesting
  List<ConnectorResponseDTO> filterConnectorResponse(
      List<ConnectorResponseDTO> connectorResponseDTO, List<EntityDetail> connectorUsages) {
    List<ConnectorResponseDTO> filteredConnectorResponse = new ArrayList<>();

    for (ConnectorResponseDTO responseDTO : connectorResponseDTO) {
      if (isInlineConnectorOrNewGitFlowIsEnabled(responseDTO)) {
        // will come here if the Connector is for a Pipeline which is Inline or has New-Git-Flow enabled
        if (responseDTO.getConnector() != null && !EmptyPredicate.isEmpty(responseDTO.getConnector().getIdentifier())) {
          log.info(
              "Preflight will be run on ConnectorDisconnectHandler with identifier: {}, this ConnectorDisconnectHandler is either Inline Pipeline or New-Git-Experience IsEnabled.",
              responseDTO.getConnector().getIdentifier());
        } else {
          log.info("Connector is either null or empty. Hence rejected for Preflight checks");
        }
        filteredConnectorResponse.add(responseDTO);
      } else {
        // will come here if Old-Git-flow is enabled and our ConnectorDisconnectHandler is made for a remote Pipeline
        for (EntityDetail connectorUse : connectorUsages) {
          if (ifResponseHasAConnectorUsage(responseDTO, connectorUse)) {
            log.info("Preflight will be run on ConnectorDisconnectHandler with identifier: {}, repoIdentifier: {} and branch: {}.",
                responseDTO.getConnector().getIdentifier(), responseDTO.getGitDetails().getRepoIdentifier(),
                responseDTO.getGitDetails().getBranch());
            filteredConnectorResponse.add(responseDTO);
          } else {
            log.info("Rejecting Connector with Identifier: {}, repoIdentifier: {} and branch: {} for preflight checks.",
                responseDTO.getConnector().getIdentifier(), responseDTO.getGitDetails().getRepoIdentifier(),
                responseDTO.getGitDetails().getBranch());
          }
        }
      }
    }

    return filteredConnectorResponse;
  }

  private boolean ifResponseHasAConnectorUsage(ConnectorResponseDTO responseDTO, EntityDetail connectorUse) {
    return Objects.equals(responseDTO.getGitDetails().getBranch(), connectorUse.getEntityRef().getBranch())
        && Objects.equals(
            responseDTO.getGitDetails().getRepoIdentifier(), connectorUse.getEntityRef().getRepoIdentifier())
        && Objects.equals(responseDTO.getConnector().getIdentifier(), connectorUse.getEntityRef().getIdentifier());
  }

  /**
   * Checks whether the connectorResponse is for a ConnectorDisconnectHandler which is for an inline pipeline OR for a pipeline that has
   * New-Git_experience enabled.
   */
  private boolean isInlineConnectorOrNewGitFlowIsEnabled(ConnectorResponseDTO responseDTO) {
    return responseDTO.getGitDetails() == null || EmptyPredicate.isEmpty(responseDTO.getGitDetails().getBranch())
        || EmptyPredicate.isEmpty(responseDTO.getGitDetails().getRepoIdentifier());
  }

  public List<ConnectorResponseDTO> getConnectorResponses(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<Scope, List<String>> scopeToConnectorIdentifiers) {
    List<ConnectorResponseDTO> connectorResponses = new ArrayList<>();
    if (scopeToConnectorIdentifiers.containsKey(Scope.ACCOUNT)) {
      connectorResponses.addAll(
          getUpdatedConnectorResponses(accountIdentifier, null, null, scopeToConnectorIdentifiers, Scope.ACCOUNT));
    }
    if (scopeToConnectorIdentifiers.containsKey(Scope.ORG)) {
      connectorResponses.addAll(
          getUpdatedConnectorResponses(accountIdentifier, orgIdentifier, null, scopeToConnectorIdentifiers, Scope.ORG));
    }
    if (scopeToConnectorIdentifiers.containsKey(Scope.PROJECT)) {
      connectorResponses.addAll(getUpdatedConnectorResponses(
          accountIdentifier, orgIdentifier, projectIdentifier, scopeToConnectorIdentifiers, Scope.PROJECT));
    }
    return connectorResponses;
  }

  @VisibleForTesting
  List<ConnectorResponseDTO> getUpdatedConnectorResponses(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<Scope, List<String>> scopeToConnectorIdentifiers, Scope scope) {
    List<ConnectorResponseDTO> connectorResponses = new ArrayList<>();
    long currentPage = 0;
    long totalPages = 0;
    do {
      PageResponse<ConnectorResponseDTO> response =
          NGRestUtils.getResponse(connectorResourceClient.listConnectors(accountIdentifier, orgIdentifier,
                                      projectIdentifier, Integer.parseInt(Long.toString(currentPage)), SIZE,
                                      ConnectorFilterPropertiesDTO.builder()
                                          .connectorIdentifiers(scopeToConnectorIdentifiers.get(scope))
                                          .build(),
                                      false),
              getErrorMessage(accountIdentifier, orgIdentifier, projectIdentifier));

      if (response == null || response.getTotalItems() == 0) {
        break;
      }

      connectorResponses.addAll(response.getContent());
      totalPages = response.getTotalPages();
      currentPage++;
    } while (currentPage < totalPages);
    return connectorResponses;
  }

  @NotNull
  @VisibleForTesting
  String getErrorMessage(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (orgIdentifier == null) {
      return "Could not get ConnectorDisconnectHandler response for account: " + accountIdentifier + " after {} attempts.";
    } else if (projectIdentifier == null) {
      return "Could not get ConnectorDisconnectHandler response for account: " + accountIdentifier + ", org: " + orgIdentifier
          + " after {} attempts.";
    } else {
      return "Could not get ConnectorDisconnectHandler response for account: " + accountIdentifier + ", org: " + orgIdentifier
          + ", project: " + projectIdentifier + " after {} attempts.";
    }
  }

  public List<ConnectorCheckResponse> getConnectorCheckResponse(Map<String, Object> fqnToObjectMapMergedYaml,
      List<ConnectorResponseDTO> connectorResponses, Map<String, String> connectorIdentifierToFqn) {
    List<ConnectorCheckResponse> connectorCheckResponses = new ArrayList<>();
    for (ConnectorResponseDTO connectorResponse : connectorResponses) {
      String connectorIdentifier = connectorResponse.getConnector().getIdentifier();
      String stageIdentifier = YamlUtils.getStageIdentifierFromFqn(connectorIdentifierToFqn.get(connectorIdentifier));
      ConnectorCheckResponseBuilder checkResponse =
          ConnectorCheckResponse.builder()
              .connectorIdentifier(connectorIdentifier)
              .fqn(connectorIdentifierToFqn.get(connectorIdentifier))
              .stageIdentifier(stageIdentifier)
              .stageName(PreflightCommonUtils.getStageName(fqnToObjectMapMergedYaml, stageIdentifier));

      if (!connectorResponse.getEntityValidityDetails().isValid()) {
        checkResponse.errorInfo(PreflightCommonUtils.getInvalidConnectorInfo()).status(PreFlightStatus.FAILURE);
        connectorCheckResponses.add(checkResponse.build());
        continue;
      }

      ConnectorConnectivityDetails connectorConnectivityDetails = connectorResponse.getStatus();
      checkResponse.status(PreflightCommonUtils.getPreFlightStatus(connectorConnectivityDetails.getStatus()));

      if (EmptyPredicate.isNotEmpty(connectorConnectivityDetails.getErrorSummary())) {
        List<PreFlightCause> causes = new ArrayList<>();
        List<PreFlightResolution> resolution = new ArrayList<>();
        connectorConnectivityDetails.getErrors().forEach(error -> {
          causes.add(PreFlightCause.builder().cause(error.getReason()).build());
          resolution.add(PreFlightResolution.builder().resolution(error.getMessage()).build());
        });
        checkResponse.errorInfo(PreFlightEntityErrorInfo.builder()
                                    .causes(causes)
                                    .resolution(resolution)
                                    .summary(connectorConnectivityDetails.getErrorSummary())
                                    .build());
      }
      connectorCheckResponses.add(checkResponse.build());
    }

    List<String> availableConnectors =
        connectorResponses.stream().map(c -> c.getConnector().getIdentifier()).collect(Collectors.toList());
    for (String connectorRef : connectorIdentifierToFqn.keySet()) {
      if (availableConnectors.contains(connectorRef)) {
        continue;
      }
      String stageIdentifier = YamlUtils.getStageIdentifierFromFqn(connectorIdentifierToFqn.get(connectorRef));
      connectorCheckResponses.add(
          ConnectorCheckResponse.builder()
              .connectorIdentifier(connectorRef)
              .fqn(connectorIdentifierToFqn.get(connectorRef))
              .stageIdentifier(stageIdentifier)
              .stageName(PreflightCommonUtils.getStageName(fqnToObjectMapMergedYaml, stageIdentifier))
              .status(PreFlightStatus.FAILURE)
              .errorInfo(PreflightCommonUtils.getNotFoundErrorInfo())
              .build());
    }
    return connectorCheckResponses;
  }
}
