/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.ccm.service.impl.ConnectorEntityChangeEventUtils.lightwingAutocudDc;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.LightwingClient;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.service.intf.AzureEntityChangeEventService;
import io.harness.ccm.service.intf.GovernanceEntityChangeEventService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.NGRestUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class AzureEntityChangeEventServiceImpl implements AzureEntityChangeEventService {
  public static final String ACCOUNT_ID = "accountId";
  public static final String AZURE_INFRA_TENANT_ID = "azureInfraTenantId";
  public static final String AZURE_INFRA_SUBSCRIPTION_ID = "azureInfraSubscriptionId";
  public static final String CONNECTOR_ID = "connectorId";
  public static final String ACTION = "action";
  private static final String GOOGLE_CREDENTIALS_PATH = "CE_GCP_CREDENTIALS_PATH";
  @Inject ConnectorResourceClient connectorResourceClient;
  @Inject CENextGenConfiguration configuration;
  @Inject BigQueryService bigQueryService;
  @Inject LightwingClient lightwingClient;
  @Inject GovernanceEntityChangeEventService governanceEntityChangeEventService;

  @Override
  public boolean processAzureEntityCreateEvent(EntityChangeDTO entityChangeDTO) {
    String identifier = entityChangeDTO.getIdentifier().getValue();
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    ArrayList<ImmutableMap<String, String>> entityChangeEvents = new ArrayList<>();

    CEAzureConnectorDTO ceAzureConnectorDTO =
        (CEAzureConnectorDTO) getConnectorConfigDTO(accountIdentifier, identifier).getConnectorConfig();
    lightwingAutocudDc(CREATE_ACTION, accountIdentifier, ceAzureConnectorDTO, lightwingClient, configuration);
    if (isVisibilityFeatureEnabled(ceAzureConnectorDTO)) {
      updateEventData(CREATE_ACTION, identifier, accountIdentifier, ceAzureConnectorDTO.getTenantId(),
          ceAzureConnectorDTO.getSubscriptionId(), entityChangeEvents);
      EntityChangeEventServiceHelper.publishMessage(entityChangeEvents, configuration.getGcpConfig().getGcpProjectId(),
          configuration.getGcpConfig().getGcpAzureConnectorCrudPubSubTopic(),
          bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH));
    }
    log.info("CREATE event processed successfully for id: {}, accountId: {}, entityChangeDTO: {}", identifier,
        accountIdentifier, entityChangeDTO);
    return true;
  }

  @Override
  public boolean processAzureEntityUpdateEvent(EntityChangeDTO entityChangeDTO) {
    String identifier = entityChangeDTO.getIdentifier().getValue();
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    ArrayList<ImmutableMap<String, String>> entityChangeEvents = new ArrayList<>();

    CEAzureConnectorDTO ceAzureConnectorDTO =
        (CEAzureConnectorDTO) getConnectorConfigDTO(accountIdentifier, identifier).getConnectorConfig();
    lightwingAutocudDc(UPDATE_ACTION, accountIdentifier, ceAzureConnectorDTO, lightwingClient, configuration);
    if (isVisibilityFeatureEnabled(ceAzureConnectorDTO)) {
      updateEventData(UPDATE_ACTION, identifier, accountIdentifier, ceAzureConnectorDTO.getTenantId(),
          ceAzureConnectorDTO.getSubscriptionId(), entityChangeEvents);
      EntityChangeEventServiceHelper.publishMessage(entityChangeEvents, configuration.getGcpConfig().getGcpProjectId(),
          configuration.getGcpConfig().getGcpAzureConnectorCrudPubSubTopic(),
          bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH));
    }
    log.info("UPDATE event processed successfully for id: {}, accountId: {}, entityChangeDTO: {}", identifier,
        accountIdentifier, entityChangeDTO);
    return true;
  }

  @Override
  public boolean processAzureEntityDeleteEvent(EntityChangeDTO entityChangeDTO) {
    String identifier = entityChangeDTO.getIdentifier().getValue();
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    ArrayList<ImmutableMap<String, String>> entityChangeEvents = new ArrayList<>();

    CEAzureConnectorDTO ceAzureConnectorDTO =
        (CEAzureConnectorDTO) getConnectorConfigDTO(accountIdentifier, identifier).getConnectorConfig();
    governanceEntityChangeEventService.processConnectorDeleteEvent(
        accountIdentifier, ceAzureConnectorDTO.getSubscriptionId());
    updateEventData(DELETE_ACTION, identifier, accountIdentifier, ceAzureConnectorDTO.getTenantId(),
        ceAzureConnectorDTO.getSubscriptionId(), entityChangeEvents);
    EntityChangeEventServiceHelper.publishMessage(entityChangeEvents, configuration.getGcpConfig().getGcpProjectId(),
        configuration.getGcpConfig().getGcpAzureConnectorCrudPubSubTopic(),
        bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH));
    log.info("DELETE event processed successfully for id: {}, accountId: {}, entityChangeDTO: {}", identifier,
        accountIdentifier, entityChangeDTO);
    return true;
  }

  private boolean isVisibilityFeatureEnabled(CEAzureConnectorDTO ceAzureConnectorDTO) {
    List<CEFeatures> featuresEnabled = ceAzureConnectorDTO.getFeaturesEnabled();
    return featuresEnabled.contains(CEFeatures.VISIBILITY);
  }

  private void updateEventData(String action, String identifier, String accountIdentifier, String azureInfraTenantId,
      String azureInfraSubscriptionId, ArrayList<ImmutableMap<String, String>> entityChangeEvents) {
    log.info("Visibility feature is enabled. Prepping event for pubsub");
    entityChangeEvents.add(ImmutableMap.<String, String>builder()
                               .put(ACTION, action)
                               .put(ACCOUNT_ID, accountIdentifier)
                               .put(AZURE_INFRA_TENANT_ID, azureInfraTenantId)
                               .put(AZURE_INFRA_SUBSCRIPTION_ID, azureInfraSubscriptionId)
                               .put(CONNECTOR_ID, identifier)
                               .build());
  }

  public ConnectorInfoDTO getConnectorConfigDTO(String accountIdentifier, String connectorIdentifierRef) {
    try {
      Optional<ConnectorDTO> connectorDTO =
          NGRestUtils.getResponse(connectorResourceClient.get(connectorIdentifierRef, accountIdentifier, null, null));

      if (!connectorDTO.isPresent()) {
        throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorIdentifierRef));
      }

      return connectorDTO.get().getConnectorInfo();
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Error while getting connector information : [%s]", connectorIdentifierRef));
    }
  }
}
