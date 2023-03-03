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
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.gitintegration.baseclass.ConnectorProcessor;
import io.harness.idp.gitintegration.factory.ConnectorProcessorFactory;
import io.harness.idp.secret.service.EnvironmentSecretServiceImpl;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
public class GitIntegrationServiceImpl implements GitIntegrationService {
  ConnectorProcessorFactory connectorProcessorFactory;
  EnvironmentSecretServiceImpl environmentSecretService;

  @Override
  public void createConnectorsSecretsEnvVariable(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, ConnectorType connectorType) {
    ConnectorProcessor connectorProcessor = connectorProcessorFactory.getConnectorProcessor(connectorType);
    List<EnvironmentSecret> connectorEnvSecrets = connectorProcessor.getConnectorSecretsInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    environmentSecretService.syncK8sSecret(connectorEnvSecrets, accountIdentifier);
  }

  @Override
  public void processConnectorUpdate(Message message, EntityChangeDTO entityChangeDTO) {
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    String connectorIdentifier = entityChangeDTO.getIdentifier().getValue();
    ConnectorType connectorType =
        ConnectorType.fromString(message.getMessage().getMetadataMap().get(CONNECTOR_ENTITY_TYPE));
    createConnectorsSecretsEnvVariable(accountIdentifier, null, null, connectorIdentifier, connectorType);
  }
}
