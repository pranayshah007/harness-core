/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entityactivity.connector;

import static io.harness.ConnectorConstants.CONNECTIVITY_STATUS;
import static io.harness.NGConstants.CONNECTOR_STRING;
import static io.harness.NGConstants.CONNECTOR_TYPE_NAME;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.ng.NextGenModule.CONNECTOR_DECORATOR_SERVICE;

import io.harness.EntityType;
import io.harness.NgAutoLogContext;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.ConnectorLogContext;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.activityhistory.dto.ConnectivityCheckActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.EntityUsageActivityDetailDTO;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.polling.service.intfc.PollingService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ConnectorEntityActivityEventHandler {
  @Inject @Named(CONNECTOR_DECORATOR_SERVICE) private ConnectorService connectorService;
  @Inject private PollingService pollingService;

  public void updateActivityResultInConnectors(NGActivityDTO ngActivityDTO) {
    String accountIdentifier = ngActivityDTO.getAccountIdentifier();
    EntityDetail connectorDetails = ngActivityDTO.getReferredEntity();
    if (connectorDetails.getType() != EntityType.CONNECTORS
        || !(connectorDetails.getEntityRef() instanceof IdentifierRef)) {
      return;
    }
    IdentifierRef entityRef = (IdentifierRef) connectorDetails.getEntityRef();
    String orgIdentifier = entityRef.getOrgIdentifier();
    String projectIdentifier = entityRef.getProjectIdentifier();
    String connectorIdentifier = entityRef.getIdentifier();
    String connectorMessage = String.format(
        CONNECTOR_STRING, connectorIdentifier, ngActivityDTO.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    log.info("Updating the connector activity result for the connector {}", connectorMessage);
    Long activityTime = null;
    ConnectorValidationResult connectorValidationResult = null;
    try (AutoLogContext ignore1 =
             new NgAutoLogContext(projectIdentifier, orgIdentifier, accountIdentifier, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ConnectorLogContext(connectorIdentifier, OVERRIDE_ERROR)) {
      switch (ngActivityDTO.getType()) {
        case CONNECTIVITY_CHECK:
          ConnectivityCheckActivityDetailDTO connectivityCheckActivityDetail =
              (ConnectivityCheckActivityDetailDTO) ngActivityDTO.getDetail();
          connectorValidationResult = connectivityCheckActivityDetail.getConnectorValidationResult();
          break;
        case ENTITY_USAGE:
          EntityUsageActivityDetailDTO entityUsageActivityDetailDTO =
              (EntityUsageActivityDetailDTO) ngActivityDTO.getDetail();
          activityTime = ngActivityDTO.getActivityTime();
          connectorValidationResult =
              createConnectorValidatonResultFromEntityUsage(entityUsageActivityDetailDTO, activityTime);
          break;
        case ENTITY_UPDATE:
          processUpdateEvent(entityRef);
          activityTime = ngActivityDTO.getActivityTime();
          break;
        case ENTITY_CREATION:
          activityTime = ngActivityDTO.getActivityTime();
          break;
        default:
      }
      connectorService.updateActivityDetailsInTheConnector(accountIdentifier, orgIdentifier, projectIdentifier,
          connectorIdentifier, connectorValidationResult, activityTime);
      log.info("Completed Updating the connector heartbeat result for the connector {}", connectorMessage);
    }
  }

  private ConnectorValidationResult createConnectorValidatonResultFromEntityUsage(
      EntityUsageActivityDetailDTO entityUsageActivityDetailDTO, Long activityTime) {
    return ConnectorValidationResult.builder()
        .status(ConnectivityStatus.valueOf(entityUsageActivityDetailDTO.getActivityMetadata().get(CONNECTIVITY_STATUS)))
        .errorSummary(entityUsageActivityDetailDTO.getErrorSummary())
        .errors(entityUsageActivityDetailDTO.getErrors())
        .testedAt(activityTime)
        .build();
  }

  private void processUpdateEvent(IdentifierRef entityRef) {
    if (entityRef.getMetadata() != null
        && ConnectorType.getArtifactConnectorTypes().contains(entityRef.getMetadata().get(CONNECTOR_TYPE_NAME))) {
      log.info("Resetting polling documents perpetual tasks for account {} , connector {}",
          entityRef.getAccountIdentifier(), entityRef.getIdentifier());
      pollingService.resetPerpetualTasksForConnector(entityRef.getAccountIdentifier(), entityRef.getIdentifier());
    }
  }
}
