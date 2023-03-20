/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.service;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.gitintegration.entities.CatalogConnector;
import io.harness.idp.gitintegration.processor.base.ConnectorProcessor;
import io.harness.idp.gitintegration.processor.factory.ConnectorProcessorFactory;
import io.harness.idp.gitintegration.repositories.CatalogConnectorRepository;
import io.harness.idp.secret.service.EnvironmentSecretService;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationServiceImpl implements GitIntegrationService {
  ConnectorProcessorFactory connectorProcessorFactory;
  EnvironmentSecretService environmentSecretService;

  CatalogConnectorRepository catalogConnectorRepository;

  @Override
  public void createConnectorSecretsEnvVariable(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, ConnectorType connectorType) {
    ConnectorProcessor connectorProcessor = connectorProcessorFactory.getConnectorProcessor(connectorType);
    Pair<ConnectorInfoDTO, Map<String, EnvironmentSecret>> connectorEnvSecrets =
        connectorProcessor.getConnectorAndSecretsInfo(
            accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    environmentSecretService.syncK8sSecret(
        new ArrayList<>(connectorEnvSecrets.getSecond().values()), accountIdentifier);
  }

  @Override
  public void processConnectorUpdate(Message message, EntityChangeDTO entityChangeDTO) {
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    String connectorIdentifier = entityChangeDTO.getIdentifier().getValue();
    Optional<CatalogConnector> catalogConnector = getCatalogConnectorEntity(accountIdentifier);
    if (catalogConnector.isEmpty()) {
      return;
    }
    String infraConnectorId = catalogConnector.get().getInfraConnector().getIdentifier();
    String sourceConnectorId = catalogConnector.get().getSourceConnector().getIdentifier();
    if (connectorIdentifier.equals(infraConnectorId) || connectorIdentifier.equals(sourceConnectorId)) {
      log.info("Connector with id - {} is getting processed in IDP Service for git integration for account {}",
          connectorIdentifier, accountIdentifier);
      ConnectorType connectorType =
          ConnectorType.fromString(message.getMessage().getMetadataMap().get(CONNECTOR_ENTITY_TYPE));
      createConnectorSecretsEnvVariable(accountIdentifier, null, null, connectorIdentifier, connectorType);
    }
  }

  private Optional<CatalogConnector> getCatalogConnectorEntity(String accountIdentifier) {
    Optional<CatalogConnector> catalogConnector = catalogConnectorRepository.findByAccountIdentifier(accountIdentifier);
    return catalogConnector;
  }
}
