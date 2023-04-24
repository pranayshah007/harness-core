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
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.configmanager.ConfigType;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.mappers.ConnectorDetailsMapper;
import io.harness.idp.gitintegration.processor.base.ConnectorProcessor;
import io.harness.idp.gitintegration.processor.factory.ConnectorProcessorFactory;
import io.harness.idp.gitintegration.repositories.CatalogConnectorRepository;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.ConnectorDetails;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationServiceImpl implements GitIntegrationService {
  ConnectorProcessorFactory connectorProcessorFactory;
  BackstageEnvVariableService backstageEnvVariableService;

  CatalogConnectorRepository catalogConnectorRepository;

  ConfigManagerService configManagerService;

  private static final String TARGET_TO_REPLACE_IN_CONFIG = "HOST_VALUE";

  private static final String SUFFIX_FOR_GITHUB_APP_CONNECTOR = "_App";

  private static final String INVALID_SCHEMA_FOR_INTEGRATIONS =
      "Invalid json schema for integrations config for account - %s";

  @Override
  public void createConnectorSecretsEnvVariable(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, ConnectorType connectorType) {
    ConnectorProcessor connectorProcessor = connectorProcessorFactory.getConnectorProcessor(connectorType);
    ConnectorInfoDTO connectorInfoDTO = connectorProcessor.getConnectorInfo(accountIdentifier, connectorIdentifier);
    Map<String, BackstageEnvVariable> connectorEnvSecrets =
        connectorProcessor.getConnectorAndSecretsInfo(accountIdentifier, connectorInfoDTO);
    backstageEnvVariableService.sync(new ArrayList<>(connectorEnvSecrets.values()), accountIdentifier);
  }

  @Override
  public void processConnectorUpdate(Message message, EntityChangeDTO entityChangeDTO) {
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    String connectorIdentifier = entityChangeDTO.getIdentifier().getValue();
    Optional<CatalogConnectorEntity> catalogConnector =
        getCatalogConnectorEntity(accountIdentifier, connectorIdentifier);
    if (catalogConnector.isEmpty()) {
      return;
    }
    String infraConnectorId = catalogConnector.get().getConnectorIdentifier();
    if (connectorIdentifier.equals(infraConnectorId)) {
      log.info("Connector with id - {} is getting processed in IDP Service for git integration for account {}",
          connectorIdentifier, accountIdentifier);
      ConnectorType connectorType =
          ConnectorType.fromString(message.getMessage().getMetadataMap().get(CONNECTOR_ENTITY_TYPE));
      createConnectorSecretsEnvVariable(accountIdentifier, null, null, connectorIdentifier, connectorType);
    }
  }

  @Override
  public void createConnectorInBackstage(String accountIdentifier, String connectorIdentifier, String type) {
    try {
      ConnectorType connectorType = ConnectorType.fromString(type);
      createConnectorSecretsEnvVariable(accountIdentifier, null, null, connectorIdentifier, connectorType);
      createAppConfigForGitIntegrations(accountIdentifier, connectorIdentifier, connectorType);
    } catch (Exception e) {
      log.error("Unable to create infra connector secrets in backstage k8s, ex = {}", e.getMessage(), e);
    }
  }

  @Override
  public List<CatalogConnectorEntity> getAllConnectorDetails(String accountIdentifier) {
    return catalogConnectorRepository.findAllByAccountIdentifier(accountIdentifier);
  }

  @Override
  public Optional<CatalogConnectorEntity> findByAccountIdAndProviderType(
      String accountIdentifier, String providerType) {
    return catalogConnectorRepository.findByAccountIdentifierAndConnectorProviderType(accountIdentifier, providerType);
  }

  @Override
  public CatalogConnectorEntity saveConnectorDetails(String accountIdentifier, ConnectorDetails connectorDetails) {
    connectorDetails.setIdentifier(
        GitIntegrationUtils.replaceAccountScopeFromConnectorId(connectorDetails.getIdentifier()));
    ConnectorProcessor connectorProcessor = connectorProcessorFactory.getConnectorProcessor(
        ConnectorType.fromString(connectorDetails.getType().toString()));
    ConnectorInfoDTO connectorInfoDTO =
        connectorProcessor.getConnectorInfo(accountIdentifier, connectorDetails.getIdentifier());
    String infraConnectorType = connectorProcessor.getInfraConnectorType(connectorInfoDTO);

    Set<String> delegateSelectors = new HashSet<>();
    ConnectorConfigDTO connectorConfig = connectorInfoDTO.getConnectorConfig();
    if (connectorConfig instanceof DelegateSelectable) {
      delegateSelectors = ((DelegateSelectable) connectorConfig).getDelegateSelectors();
    }

    CatalogConnectorEntity catalogConnectorEntity =
        ConnectorDetailsMapper.fromDTO(connectorDetails, accountIdentifier, infraConnectorType, delegateSelectors);
    CatalogConnectorEntity savedCatalogConnectorEntity =
        catalogConnectorRepository.saveOrUpdate(catalogConnectorEntity);
    createConnectorInBackstage(accountIdentifier, catalogConnectorEntity.getConnectorIdentifier(),
        catalogConnectorEntity.getConnectorProviderType());
    return savedCatalogConnectorEntity;
  }

  @Override
  public CatalogConnectorEntity findDefaultConnectorDetails(String accountIdentifier) {
    return catalogConnectorRepository.findLastUpdated(accountIdentifier);
  }

  private Optional<CatalogConnectorEntity> getCatalogConnectorEntity(
      String accountIdentifier, String connectorIdentifier) {
    return catalogConnectorRepository.findByAccountIdentifierAndConnectorIdentifier(
        accountIdentifier, connectorIdentifier);
  }

  public void createAppConfigForGitIntegrations(
      String accountIdentifier, String connectorIdentifier, ConnectorType connectorType) throws Exception {
    ConnectorProcessor connectorProcessor = connectorProcessorFactory.getConnectorProcessor(connectorType);
    ConnectorInfoDTO connectorInfoDTO = connectorProcessor.getConnectorInfo(accountIdentifier, connectorIdentifier);
    String host = GitIntegrationUtils.getHostForConnector(connectorInfoDTO, connectorType);
    String connectorTypeAsString = connectorType.toString();
    if (connectorType == ConnectorType.GITHUB && GitIntegrationUtils.checkIfGithubAppConnector(connectorInfoDTO)) {
      connectorTypeAsString = connectorTypeAsString + SUFFIX_FOR_GITHUB_APP_CONNECTOR;
    }
    String integrationConfigs = ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(connectorTypeAsString);
    log.info("Connector chosen in git integration is  - {} ", connectorTypeAsString);
    integrationConfigs = integrationConfigs.replace(TARGET_TO_REPLACE_IN_CONFIG, host);

    String schemaForIntegrations =
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(connectorTypeAsString);
    if (!ConfigManagerUtils.isValidSchema(integrationConfigs, schemaForIntegrations)) {
      log.error(String.format(INVALID_SCHEMA_FOR_INTEGRATIONS, accountIdentifier));
    }

    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(connectorTypeAsString);
    appConfig.setConfigs(integrationConfigs);
    appConfig.setEnabled(true);

    try {
      configManagerService.saveConfigForAccount(appConfig, accountIdentifier, ConfigType.INTEGRATION);
      configManagerService.mergeAndSaveAppConfig(accountIdentifier);
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    log.info("Merging for git integration completed for connector - {}", connectorTypeAsString);
  }
}
