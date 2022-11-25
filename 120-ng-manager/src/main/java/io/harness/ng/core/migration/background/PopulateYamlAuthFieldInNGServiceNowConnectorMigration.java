/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.persistence.HQuery.excludeAuthority;

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
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.repositories.ConnectorRepository;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PopulateYamlAuthFieldInNGServiceNowConnectorMigration implements NGMigration {
  @Inject private HPersistence persistence;
  @Inject private ConnectorRepository connectorRepository;
  private static final String DEBUG_LOG = "[ServiceNowConnectorAuthMigration]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration to ServiceNowAuthenticationDTO in ServiceNow connector");
      Query<ServiceNowConnector> serviceNowConnectorQuery =
          persistence.createQuery(ServiceNowConnector.class, excludeAuthority)
              .field(ServiceNowConnector.ConnectorKeys.type)
              .equal(ConnectorType.SERVICENOW);

      try (HIterator<ServiceNowConnector> iterator = new HIterator<>(serviceNowConnectorQuery.fetch())) {
        for (ServiceNowConnector serviceNowConnector : iterator) {
          if (!isNull(serviceNowConnector.getServiceNowAuthentication())
              || !isNull(serviceNowConnector.getAuthType())) {
            log.info(String.format(
                "%s Skipping since serviceNow connector with identifier %s in account %s, org %s, project %s already has authentication object as %s and auth type as %s",
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
      }
      log.info(
          DEBUG_LOG + "Migration of adding auth type and serviceNow authentication to serviceNow connector completed");
    } catch (Exception e) {
      log.error(
          DEBUG_LOG + "Migration of adding auth type and serviceNow authentication to serviceNow connector failed", e);
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
              "%s Failed trying to add serviceNowAuthentication for serviceNow connector with identifier %s in account %s, org %s, project %s",
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
              "%s Failed trying to save modified serviceNow connector with identifier %s in account %s, org %s, project %s",
              DEBUG_LOG, serviceNowConnector.getIdentifier(), serviceNowConnector.getAccountIdentifier(),
              serviceNowConnector.getOrgIdentifier(), serviceNowConnector.getProjectIdentifier()),
          exception);
    }
  }
}