/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.helpers.GlobalSecretManagerUtils.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.migration.NGMigration;
import io.harness.ng.core.migration.NGSecretManagerMigration;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class CreateDefaultGcpKmsSMInNGMigration implements NGMigration {
  private final NGSecretManagerMigration ngSecretManagerMigration;
  private final ConnectorService connectorService;

  @Inject
  public CreateDefaultGcpKmsSMInNGMigration(NGSecretManagerMigration ngSecretManagerMigration,
      @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService, ConnectorService connectorService1) {
    this.ngSecretManagerMigration = ngSecretManagerMigration;
    this.connectorService = connectorService1;
  }

  @Override
  public void migrate() {
    try {
      Optional<ConnectorResponseDTO> connectorResponseDTO =
          connectorService.get(GLOBAL_ACCOUNT_ID, null, null, HARNESS_SECRET_MANAGER_IDENTIFIER);
      // No migration for clusters where Global GCP KMS already exists
      if (connectorResponseDTO.isPresent()
          && connectorResponseDTO.get().getConnector().getConnectorType() == ConnectorType.GCP_KMS) {
        return;
      }
    } catch (Exception e) {
      log.error(
          "[CreateDefaultGcpKMSInNGMigration]: Error while fetching Global SM for accountId {}", GLOBAL_ACCOUNT_ID);
      return;
    }
    log.info("[CreateDefaultGcpKMSInNGMigration]: Creating global SM.");
    ConnectorDTO globalConnectorDTO =
        ngSecretManagerMigration.createGlobalGcpKmsSM(GLOBAL_ACCOUNT_ID, null, null, true);
    log.info("[CreateDefaultGcpKMSInNGMigration]: Global SM Created Successfully.");
  }
}
