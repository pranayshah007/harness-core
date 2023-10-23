/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.connector.entities.Connector.ConnectorKeys;
import static io.harness.connector.entities.embedded.gitlabconnector.GitlabConnector.GitlabConnectorKeys;

import io.harness.connector.entities.embedded.gitlabconnector.GitlabTokenApiAccess;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.migration.NGMigration;
import io.harness.repositories.ConnectorRepository;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class NGGitlabConnectorMigration implements NGMigration {
  @Inject private ConnectorRepository connectorRepository;
  private static final int BATCH_SIZE = 100;
  private static final String classField = GitlabConnectorKeys.gitlabApiAccess + "._class";

  @Override
  public void migrate() {
    try {
      Criteria criteria = Criteria.where(ConnectorKeys.type)
                              .is(ConnectorType.GITLAB)
                              .and(GitlabConnectorKeys.hasApiAccess)
                              .is(Boolean.TRUE)
                              .and(classField)
                              .exists(false)
                              .and(GitlabConnectorKeys.apiAccessType)
                              .exists(false);
      Query query = new Query(criteria);
      query.cursorBatchSize(BATCH_SIZE);
      Update update = new Update();
      update.set(classField, GitlabTokenApiAccess.class.getCanonicalName());
      update.set(GitlabConnectorKeys.apiAccessType, GitlabApiAccessType.TOKEN);
      log.info("[NGGitlabConnectorMigration] Query for updating Harness Gitlab ConnectorDisconnectHandler Access: {}", query.toString());

      UpdateResult result = connectorRepository.updateMultiple(query, update);

      log.info("[NGGitlabConnectorMigration] Successfully updated {} Gitlab ConnectorDisconnectHandler api access type",
          result.getModifiedCount());
    } catch (Exception e) {
      log.error("[NGGitlabConnectorMigration] Failed to update Gitlab ConnectorDisconnectHandler api access type. Error: {}", e);
    }
  }
}
