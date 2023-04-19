/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.stats.ConnectorStatistics;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.DX)
public interface ConnectorService extends ConnectorCrudService, ConnectorValidationService, GitRepoConnectorService {
  boolean validateTheIdentifierIsUnique(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier);

  ConnectorStatistics getConnectorStatistics(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  String getHeartbeatPerpetualTaskId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  /**
   * @return Accepts a list of pairs (accountId, perpetualTaskId) and resets the perpetual task for given config
   */
  void resetHeartbeatForReferringConnectors(List<Pair<String, String>> connectorPerpetualTaskInfoList);

  void resetHeartBeatTask(String accountId, String taskId);

  boolean checkConnectorExecutableOnDelegate(ConnectorInfoDTO connectorInfo);

  boolean markEntityInvalid(String accountIdentifier, EntityReference entityReference, String invalidYaml);

  ConnectorDTO fullSyncEntity(EntityDetailProtoDTO entityDetailProtoDTO, boolean isFullSyncingToDefaultBranch);

  ConnectorResponseDTO updateGitFilePath(ConnectorDTO connectorDTO, String accountIdentifier, String newFilePath);

  List<Map<String, String>> getAttributes(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> connectorIdentifiers);

  Long countConnectors(String accountIdentifier);
}
