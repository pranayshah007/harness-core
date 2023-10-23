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
import io.harness.connector.entities.embedded.jira.JiraAuthentication;
import io.harness.connector.entities.embedded.jira.JiraConnector;
import io.harness.connector.entities.embedded.jira.JiraConnector.JiraConnectorKeys;
import io.harness.connector.entities.embedded.jira.JiraUserNamePasswordAuthentication;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
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

/**
 * Migration to add auth object in existing jira connectors in NG.
 *
 * Granularity of the migration written according to jira connectors count in production at the time of writing the
 * migration
 *
 */
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PopulateYamlAuthFieldInNGJiraConnectorMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private ConnectorRepository connectorRepository;
  private static final String DEBUG_LOG = "[JiraConnectorAuthMigration]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration to JiraAuthenticationDTO in Jira ConnectorDisconnectHandler");
      List<JiraConnector> jiraConnectors = new ArrayList<>();
      try {
        Criteria jiraConnectorCriteria = Criteria.where(JiraConnector.ConnectorKeys.type).is(ConnectorType.JIRA);
        Query jiraConnectorQuery = new Query(jiraConnectorCriteria);
        jiraConnectors = mongoTemplate.find(jiraConnectorQuery, JiraConnector.class);
        if (isEmpty(jiraConnectors)) {
          log.info(String.format("%s no jira connectors fetched", DEBUG_LOG));
          return;
        }
        log.info(String.format("%s Running migration on %s jira connectors", DEBUG_LOG, jiraConnectors.size()));
      } catch (Exception e) {
        log.error(DEBUG_LOG + " Failed trying to fetch jira connectors", e);
      }

      for (JiraConnector jiraConnector : jiraConnectors) {
        if (!isNull(jiraConnector.getJiraAuthentication()) || !isNull(jiraConnector.getAuthType())) {
          log.info(String.format(
              "%s Skipping since jira ConnectorDisconnectHandler with identifier %s in account %s, org %s, project %s already has authentication object as %s and auth type as %s",
              DEBUG_LOG, jiraConnector.getIdentifier(), jiraConnector.getAccountIdentifier(),
              jiraConnector.getOrgIdentifier(), jiraConnector.getProjectIdentifier(),
              jiraConnector.getJiraAuthentication(), jiraConnector.getAuthType()));
          continue;
        }
        JiraAuthentication jiraAuthentication = mapBaseLevelAuthToJiraAuthentication(jiraConnector);

        if (jiraAuthentication == null) {
          continue;
        }
        findAndModifyJiraConnector(jiraConnector, jiraAuthentication);
      }
      log.info(DEBUG_LOG + "Migration of adding auth type and jira authentication to jira ConnectorDisconnectHandler completed");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Migration of adding auth type and jira authentication to jira ConnectorDisconnectHandler failed", e);
    }
  }

  public JiraAuthentication mapBaseLevelAuthToJiraAuthentication(JiraConnector jiraConnector) {
    try {
      if (isNull(jiraConnector)) {
        return null;
      }
      return JiraUserNamePasswordAuthentication.builder()
          .username(jiraConnector.getUsername())
          .usernameRef(jiraConnector.getUsernameRef())
          .passwordRef(jiraConnector.getPasswordRef())
          .build();
    } catch (Exception exception) {
      log.error(
          String.format(
              "%s Failed trying to add jiraAuthentication for jira ConnectorDisconnectHandler with identifier %s in account %s, org %s, project %s",
              DEBUG_LOG, jiraConnector.getIdentifier(), jiraConnector.getAccountIdentifier(),
              jiraConnector.getOrgIdentifier(), jiraConnector.getProjectIdentifier()),
          exception);
    }
    return null;
  }

  public void findAndModifyJiraConnector(
      JiraConnector jiraConnector, JiraAuthentication jiraUserNamePasswordAuthentication) {
    try {
      Criteria jiraConnectorIdCriteria = Criteria.where(JiraConnector.ConnectorKeys.identifier)
                                             .is(jiraConnector.getIdentifier())
                                             .and(JiraConnector.ConnectorKeys.accountIdentifier)
                                             .is(jiraConnector.getAccountIdentifier())
                                             .and(JiraConnector.ConnectorKeys.orgIdentifier)
                                             .is(jiraConnector.getOrgIdentifier())
                                             .and(JiraConnector.ConnectorKeys.projectIdentifier)
                                             .is(jiraConnector.getProjectIdentifier());

      Update update = new Update();
      update.set(JiraConnectorKeys.authType, JiraAuthType.USER_PASSWORD)
          .set(JiraConnectorKeys.jiraAuthentication, jiraUserNamePasswordAuthentication);

      connectorRepository.update(jiraConnectorIdCriteria, update, ChangeType.NONE, jiraConnector.getProjectIdentifier(),
          jiraConnector.getOrgIdentifier(), jiraConnector.getAccountIdentifier());

    } catch (Exception exception) {
      log.error(
          String.format(
              "%s Failed trying to save modified jira ConnectorDisconnectHandler with identifier %s in account %s, org %s, project %s",
              DEBUG_LOG, jiraConnector.getIdentifier(), jiraConnector.getAccountIdentifier(),
              jiraConnector.getOrgIdentifier(), jiraConnector.getProjectIdentifier()),
          exception);
    }
  }
}