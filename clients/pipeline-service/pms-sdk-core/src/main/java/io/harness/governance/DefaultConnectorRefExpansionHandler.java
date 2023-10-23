/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.encryption.Scope;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.util.Optional;

@OwnedBy(PIPELINE)
@Singleton
public class DefaultConnectorRefExpansionHandler implements JsonExpansionHandler {
  @Inject ConnectorResourceClient connectorResourceClient;
  @Inject PmsGitSyncHelper gitSyncHelper;

  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    String accountId = metadata.getAccountId();
    String orgId = metadata.getOrgId();
    String projectId = metadata.getProjectId();
    ByteString gitSyncBranchContext = metadata.getGitSyncBranchContext();
    String scopedConnectorId = fieldValue.textValue();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(scopedConnectorId, accountId, orgId, projectId);
    Scope scope = identifierRef.getScope();
    Optional<ConnectorDTO> optConnector;
    switch (scope) {
      case ACCOUNT:
        optConnector = getConnectorDTO(identifierRef.getIdentifier(), accountId, null, null);
        break;
      case ORG:
        optConnector = getConnectorDTO(identifierRef.getIdentifier(), accountId, orgId, null);
        break;
      case PROJECT:
        optConnector =
            getConnectorDTO(identifierRef.getIdentifier(), accountId, orgId, projectId, gitSyncBranchContext);
        break;
      default:
        return sendErrorResponseForNotFoundConnector(scopedConnectorId);
    }
    if (!optConnector.isPresent()) {
      return sendErrorResponseForNotFoundConnector(scopedConnectorId);
    }
    ConnectorDTO connector = optConnector.get();
    ExpandedValue value = ConnectorRefExpandedValue.builder().connectorDTO(connector).build();
    return ExpansionResponse.builder()
        .success(true)
        .key(value.getKey())
        .value(value)
        .placement(ExpansionPlacementStrategy.REPLACE)
        .build();
  }

  Optional<ConnectorDTO> getConnectorDTO(
      String connectorId, String accountId, String orgId, String projectId, ByteString gitSyncBranchContext) {
    try (PmsGitSyncBranchContextGuard ignore =
             gitSyncHelper.createGitSyncBranchContextGuardFromBytes(gitSyncBranchContext, true)) {
      return getConnectorDTO(connectorId, accountId, orgId, projectId);
    }
  }

  Optional<ConnectorDTO> getConnectorDTO(String connectorId, String accountId, String orgId, String projectId) {
    return NGRestUtils.getResponse(connectorResourceClient.get(connectorId, accountId, orgId, projectId),
        "Could not get ConnectorDisconnectHandler response for account: " + accountId + " after {} attempts.");
  }

  ExpansionResponse sendErrorResponseForNotFoundConnector(String scopedConnectorId) {
    return ExpansionResponse.builder()
        .success(false)
        .errorMessage("Could not find ConnectorDisconnectHandler: " + scopedConnectorId)
        .build();
  }
}
