/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class DeleteSoftDeletedConnectorsMigration implements NGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private AccountUtils accountUtils;
  private static final String DEBUG_LOG = "[DeleteSoftDeletedConnectorsMigration]: ";
  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting deletion of soft deleted connectors");
      List<String> accountIdentifiers = accountUtils.getAllNGAccountIds();
      accountIdentifiers.forEach(accountId -> {
        try {
          DBCollection collection = hPersistence.getCollection(Connector.class);
          BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          bulkWriteOperation
              .find(hPersistence.createQuery(Connector.class, excludeAuthority)
                        .filter(ConnectorKeys.accountIdentifier, accountId)
                        .filter(ConnectorKeys.deleted, true)
                        .getQueryObject())
              .remove();
          bulkWriteOperation.execute();
        } catch (Exception e) {
          log.error(DEBUG_LOG + "deletion of soft deleted connectors failed for account : " + accountId, e);
        }
      });
      log.info(DEBUG_LOG + "deletion of soft deleted connectors completed");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "deletion of soft deleted connectors failed.", e);
    }
  }
}
