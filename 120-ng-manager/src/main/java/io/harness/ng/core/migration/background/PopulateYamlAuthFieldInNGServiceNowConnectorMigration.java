/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.servicenow.ServiceNowAuthentication;
import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector;
import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector.ServiceNowConnectorKeys;
import io.harness.connector.entities.embedded.servicenow.ServiceNowUserNamePasswordAuthentication;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.git.model.ChangeType;
import io.harness.migration.NGMigration;
import io.harness.repositories.ConnectorRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PopulateYamlAuthFieldInNGServiceNowConnectorMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private ConnectorRepository connectorRepository;
  private static final String DEBUG_LOG = "[ServiceNowConnectorAuthMigration]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration to ServiceNowAuthenticationDTO in ServiceNow ConnectorDisconnectHandler");
      List<ServiceNowConnector> serviceNowConnectors = new ArrayList<>();
      try {
        Criteria serviceNowConnectorCriteria =
            Criteria.where(ServiceNowConnector.ConnectorKeys.type).is(ConnectorType.SERVICENOW);
        Query serviceNowConnectorQuery = new Query(serviceNowConnectorCriteria);
        serviceNowConnectors = mongoTemplate.find(serviceNowConnectorQuery, ServiceNowConnector.class);
        if (isEmpty(serviceNowConnectors)) {
          log.info(String.format("%s no serviceNow connectors fetched", DEBUG_LOG));
          return;
        }
        log.info(
            String.format("%s Running migration on %s serviceNow connectors", DEBUG_LOG, serviceNowConnectors.size()));
      } catch (Exception e) {
        log.error(DEBUG_LOG + " Failed trying to fetch servicenow connectors", e);
      }

      for (ServiceNowConnector serviceNowConnector : serviceNowConnectors) {
        if (!isNull(serviceNowConnector.getServiceNowAuthentication()) || !isNull(serviceNowConnector.getAuthType())) {
          log.info(String.format(
              "%s Skipping since serviceNow ConnectorDisconnectHandler with identifier %s in account %s, org %s, project %s already has authentication object as %s and auth type as %s",
              DEBUG_LOG, serviceNowConnector.getIdentifier(), serviceNowConnector.getAccountIdentifier(),
              serviceNowConnector.getOrgIdentifier(), serviceNowConnector.getProjectIdentifier(),
              serviceNowConnector.getServiceNowAuthentication(), serviceNowConnector.getAuthType()));
          continue;
        }
        ServiceNowAuthentication serviceNowAuthentication =
            mapBaseLevelAuthToServiceNowAuthentication(serviceNowConnector);

        if (serviceNowAuthentication == null) {
          continue;
        }
        findAndModifyServiceNowConnector(serviceNowConnector, serviceNowAuthentication);
      }
      log.info(
          DEBUG_LOG + "Migration of adding auth type and serviceNow authentication to serviceNow ConnectorDisconnectHandler completed");
    } catch (Exception e) {
      log.error(
          DEBUG_LOG + "Migration of adding auth type and serviceNow authentication to serviceNow ConnectorDisconnectHandler failed", e);
    }
  }

  public ServiceNowAuthentication mapBaseLevelAuthToServiceNowAuthentication(ServiceNowConnector serviceNowConnector) {
    try {
      if (isNull(serviceNowConnector)) {
        return null;
      }
      return ServiceNowUserNamePasswordAuthentication.builder()
          .username(serviceNowConnector.getUsername())
          .usernameRef(serviceNowConnector.getUsernameRef())
          .passwordRef(serviceNowConnector.getPasswordRef())
          .build();
    } catch (Exception exception) {
      log.error(
          String.format(
              "%s Failed trying to add serviceNowAuthentication for serviceNow ConnectorDisconnectHandler with identifier %s in account %s, org %s, project %s",
              DEBUG_LOG, serviceNowConnector.getIdentifier(), serviceNowConnector.getAccountIdentifier(),
              serviceNowConnector.getOrgIdentifier(), serviceNowConnector.getProjectIdentifier()),
          exception);
    }
    return null;
  }

  public void findAndModifyServiceNowConnector(
      ServiceNowConnector serviceNowConnector, ServiceNowAuthentication serviceNowUserNamePasswordAuthentication) {
    try {
      Criteria serviceNowConnectorIdCriteria = Criteria.where(ServiceNowConnector.ConnectorKeys.identifier)
                                                   .is(serviceNowConnector.getIdentifier())
                                                   .and(ServiceNowConnector.ConnectorKeys.accountIdentifier)
                                                   .is(serviceNowConnector.getAccountIdentifier())
                                                   .and(ServiceNowConnector.ConnectorKeys.orgIdentifier)
                                                   .is(serviceNowConnector.getOrgIdentifier())
                                                   .and(ServiceNowConnector.ConnectorKeys.projectIdentifier)
                                                   .is(serviceNowConnector.getProjectIdentifier());

      Update update = new Update();
      update.set(ServiceNowConnectorKeys.authType, ServiceNowAuthType.USER_PASSWORD)
          .set(ServiceNowConnectorKeys.serviceNowAuthentication, serviceNowUserNamePasswordAuthentication);

      connectorRepository.update(serviceNowConnectorIdCriteria, update, ChangeType.NONE,
          serviceNowConnector.getProjectIdentifier(), serviceNowConnector.getOrgIdentifier(),
          serviceNowConnector.getAccountIdentifier());

    } catch (Exception exception) {
      log.error(
          String.format(
              "%s Failed trying to save modified serviceNow ConnectorDisconnectHandler with identifier %s in account %s, org %s, project %s",
              DEBUG_LOG, serviceNowConnector.getIdentifier(), serviceNowConnector.getAccountIdentifier(),
              serviceNowConnector.getOrgIdentifier(), serviceNowConnector.getProjectIdentifier()),
          exception);
    }
  }
}