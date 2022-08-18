/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.service.intf.GCPEntityChangeEventService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.gcpccm.GcpCloudCostConnectorDTO;
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
public class GCPEntityChangeEventServiceImpl implements GCPEntityChangeEventService {
  public static final String ACCOUNT_ID = "accountId";
  public static final String GCP_INFRA_PROJECT_ID = "gcpInfraProjectId";
  public static final String GCP_INFRA_SERVICE_ACCOUNT_EMAIL = "gcpInfraServiceAccountEmail";
  public static final String CONNECTOR_ID = "connectorId";
  public static final String ACTION = "action";
  private static final String GOOGLE_CREDENTIALS_PATH = "CE_GCP_CREDENTIALS_PATH";
  @Inject ConnectorResourceClient connectorResourceClient;
  @Inject CENextGenConfiguration configuration;
  @Inject BigQueryService bigQueryService;

  @Override
  public boolean processGCPEntityCreateEvent(EntityChangeDTO entityChangeDTO) {
    String identifier = entityChangeDTO.getIdentifier().getValue();
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    ArrayList<ImmutableMap<String, String>> entityChangeEvents = new ArrayList<>();

    GcpCloudCostConnectorDTO gcpCloudCostConnectorDTO =
        (GcpCloudCostConnectorDTO) getConnectorConfigDTO(accountIdentifier, identifier).getConnectorConfig();
    if (isVisibilityFeatureEnabled(gcpCloudCostConnectorDTO)) {
      updateEventData(CREATE_ACTION, identifier, accountIdentifier, gcpCloudCostConnectorDTO.getProjectId(),
          gcpCloudCostConnectorDTO.getServiceAccountEmail(), entityChangeEvents);
      EntityChangeEventServiceHelper.publishMessage(entityChangeEvents, configuration.getGcpConfig().getGcpProjectId(),
          configuration.getGcpConfig().getGcpGcpConnectorCrudPubSubTopic(),
          bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH));
    }
    log.info("CREATE event processed successfully for id: {}, accountId: {}, entityChangeDTO: {}", identifier,
        accountIdentifier, entityChangeDTO);
    return true;
  }

  @Override
  public boolean processGCPEntityUpdateEvent(EntityChangeDTO entityChangeDTO) {
    String identifier = entityChangeDTO.getIdentifier().getValue();
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    ArrayList<ImmutableMap<String, String>> entityChangeEvents = new ArrayList<>();

    GcpCloudCostConnectorDTO gcpCloudCostConnectorDTO =
        (GcpCloudCostConnectorDTO) getConnectorConfigDTO(accountIdentifier, identifier).getConnectorConfig();
    if (isVisibilityFeatureEnabled(gcpCloudCostConnectorDTO)) {
      updateEventData(UPDATE_ACTION, identifier, accountIdentifier, gcpCloudCostConnectorDTO.getProjectId(),
          gcpCloudCostConnectorDTO.getServiceAccountEmail(), entityChangeEvents);
      EntityChangeEventServiceHelper.publishMessage(entityChangeEvents, configuration.getGcpConfig().getGcpProjectId(),
          configuration.getGcpConfig().getGcpGcpConnectorCrudPubSubTopic(),
          bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH));
    }
    log.info("UPDATE event processed successfully for id: {}, accountId: {}, entityChangeDTO: {}", identifier,
        accountIdentifier, entityChangeDTO);
    return true;
  }

  @Override
  public boolean processGCPEntityDeleteEvent(EntityChangeDTO entityChangeDTO) {
    String identifier = entityChangeDTO.getIdentifier().getValue();
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    ArrayList<ImmutableMap<String, String>> entityChangeEvents = new ArrayList<>();
    updateEventData(DELETE_ACTION, identifier, accountIdentifier, "", "", entityChangeEvents);
    EntityChangeEventServiceHelper.publishMessage(entityChangeEvents, configuration.getGcpConfig().getGcpProjectId(),
        configuration.getGcpConfig().getGcpGcpConnectorCrudPubSubTopic(),
        bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH));
    log.info("DELETE event processed successfully for id: {}, accountId: {}, entityChangeDTO: {}", identifier,
        accountIdentifier, entityChangeDTO);
    return true;
  }

  private boolean isVisibilityFeatureEnabled(GcpCloudCostConnectorDTO gcpCloudCostConnectorDTO) {
    List<CEFeatures> featuresEnabled = gcpCloudCostConnectorDTO.getFeaturesEnabled();
    return featuresEnabled.contains(CEFeatures.VISIBILITY);
  }

  private void updateEventData(String action, String identifier, String accountIdentifier, String gcpInfraProjectId,
      String gcpInfraServiceAccountEmail, ArrayList<ImmutableMap<String, String>> entityChangeEvents) {
    log.info("Visibility feature is enabled. Prepping event for pubsub");
    entityChangeEvents.add(ImmutableMap.<String, String>builder()
                               .put(ACTION, action)
                               .put(ACCOUNT_ID, accountIdentifier)
                               .put(GCP_INFRA_PROJECT_ID, gcpInfraProjectId)
                               .put(GCP_INFRA_SERVICE_ACCOUNT_EMAIL, gcpInfraServiceAccountEmail)
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
