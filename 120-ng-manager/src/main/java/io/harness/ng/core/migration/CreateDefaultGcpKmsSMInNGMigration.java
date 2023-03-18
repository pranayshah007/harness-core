/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.connector.entities.Connector.ConnectorKeys;
import static io.harness.helpers.GlobalSecretManagerUtils.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.Connector;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
@Slf4j
public class CreateDefaultGcpKmsSMInNGMigration implements NGMigration {
  private final NGSecretManagerMigration ngSecretManagerMigration;
  private final ConnectorService connectorService;
  private MongoTemplate mongoTemplate;

  @Inject
  public CreateDefaultGcpKmsSMInNGMigration(NGSecretManagerMigration ngSecretManagerMigration,
      @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService, MongoTemplate mongoTemplate) {
    this.ngSecretManagerMigration = ngSecretManagerMigration;
    this.connectorService = connectorService;
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void migrate() {
    Optional<ConnectorResponseDTO> connectorResponseDTO;
    ConnectorDTO globalConnectorDTO;
    try {
      connectorResponseDTO = connectorService.get(GLOBAL_ACCOUNT_ID, null, null, HARNESS_SECRET_MANAGER_IDENTIFIER);
      // No migration for clusters where Global GCP KMS already exists
      if (connectorResponseDTO.isPresent()
          && connectorResponseDTO.get().getConnector().getConnectorType() == ConnectorType.GCP_KMS) {
        globalConnectorDTO = ConnectorDTO.builder().connectorInfo(connectorResponseDTO.get().getConnector()).build();
        createGcpKmsCopiesInAllScopes(globalConnectorDTO);
      } else {
        Connector localGlobalConnector = removeGlobalLocalConnector();
        globalConnectorDTO = createGlobalGcpKMS(localGlobalConnector);
        createGcpKmsCopiesInAllScopes(globalConnectorDTO);
      }
    } catch (Exception e) {
      log.error("[CreateDefaultGcpKMSInNGMigration]: Error in completing migration {}", GLOBAL_ACCOUNT_ID);
      return;
    }
  }

  private void createGcpKmsCopiesInAllScopes(ConnectorDTO globalConnectorDTO) {
    // if there are no LOCAL connector except one then migration is not needed.
    Query query = new Query(Criteria.where(ConnectorKeys.identifier)
                                .is(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                .and(ConnectorKeys.type)
                                .is(ConnectorType.LOCAL));
    final List<Connector> result = mongoTemplate.find(query, Connector.class);
    if (result != null && result.size() == 0) {
      log.info("[CreateDefaultGcpKMSInNGMigration] No Local Connector left to migrate");
      return;
    }

    if (globalConnectorDTO != null) {
      List<String> allAccounts = ngSecretManagerMigration.fetchAllAccounts();
      ngSecretManagerMigration.populateHarnessManagedDefaultKms(allAccounts, globalConnectorDTO);
      log.info("[CreateDefaultGcpKMSInNGMigration] HarnessManaged KMS Created/Updated");
    }
  }

  private ConnectorDTO createGlobalGcpKMS(Connector localGlobalConnector) {
    ConnectorDTO globalConnectorDTO;
    try {
      log.info("[CreateDefaultGcpKMSInNGMigration]: Creating global SM.");
      globalConnectorDTO = ngSecretManagerMigration.createGlobalGcpKmsSM(GLOBAL_ACCOUNT_ID, null, null, true);
      log.info("[CreateDefaultGcpKMSInNGMigration]: Global SM Created Successfully.");
    } catch (Exception e) {
      log.info("[CreateDefaultGcpKMSInNGMigration]: Error while creating global SM ", e);
      mongoTemplate.save(localGlobalConnector);
      throw e;
    }
    return globalConnectorDTO;
  }

  private Connector removeGlobalLocalConnector() throws Exception {
    log.info("[CreateDefaultGcpKMSInNGMigration]: Removing GLOBAL Local Connector");
    Query query = new Query(Criteria.where(ConnectorKeys.identifier)
                                .is(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                .and(ConnectorKeys.accountIdentifier)
                                .is(GLOBAL_ACCOUNT_ID)
                                .and(ConnectorKeys.type)
                                .is(ConnectorType.LOCAL));
    final Connector connector = mongoTemplate.findAndRemove(query, Connector.class);

    if (connector != null) {
      log.info("[CreateDefaultGcpKMSInNGMigration]: Removed GLOBAL Local Connector");
    } else {
      log.error("[CreateDefaultGcpKMSInNGMigration]: Unable to remove GLOBAL Local Connector");
      throw new Exception("Unable to remove GLOBAL Local Connector");
    }
    return connector;
  }
}
