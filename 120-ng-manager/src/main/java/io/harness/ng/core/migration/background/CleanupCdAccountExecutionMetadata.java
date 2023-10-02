/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.pipeline.executions.CDAccountExecutionMetadata;
import io.harness.cdng.pipeline.executions.CDAccountExecutionMetadata.CDAccountExecutionMetadataKeys;
import io.harness.migration.NGMigration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import dev.morphia.query.Query;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CleanupCdAccountExecutionMetadata implements NGMigration {
  @Inject private AccountUtils accountUtils;
  @Inject private HPersistence hPersistence;

  private static final String DEBUG_LOG = "[CleanupCdAccountExecutionMetadata]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting deletion of CDAccountExecutionMetadata for deleted accounts");

      HashSet<String> existingAccounts = new HashSet<>(accountUtils.getAllNGAccountIds());
      HashSet<String> accountsForDeletion = getAccountsForDeletion();
      accountsForDeletion.removeAll(existingAccounts);

      accountsForDeletion.forEach(accountId -> {
        try {
          DBCollection collection = hPersistence.getCollection(CDAccountExecutionMetadata.class);
          BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          bulkWriteOperation
              .find(hPersistence.createQuery(CDAccountExecutionMetadata.class, excludeAuthority)
                        .filter(CDAccountExecutionMetadataKeys.accountId, accountId)
                        .getQueryObject())
              .remove();
          bulkWriteOperation.execute();
        } catch (Exception e) {
          log.error(DEBUG_LOG + "deletion of CDAccountExecutionMetadata failed for account : " + accountId, e);
        }
      });
      log.info(DEBUG_LOG + "deletion of CDAccountExecutionMetadata for deleted accounts completed");

    } catch (Exception e) {
      log.error(DEBUG_LOG + "deletion of CDAccountExecutionMetadata for deleted accounts failed.", e);
    }
  }

  @NotNull
  private HashSet<String> getAccountsForDeletion() {
    Query<CDAccountExecutionMetadata> query = hPersistence.createQuery(CDAccountExecutionMetadata.class)
                                                  .project(CDAccountExecutionMetadataKeys.accountId, true)
                                                  .limit(NO_LIMIT);

    HashSet<String> accountsForDeletion = new HashSet<>();
    try (HIterator<CDAccountExecutionMetadata> iterator = new HIterator<>(query.fetch())) {
      for (CDAccountExecutionMetadata cdAccountExecutionMetadata : iterator) {
        accountsForDeletion.add(cdAccountExecutionMetadata.getAccountId());
      }
    }
    return accountsForDeletion;
  }
}
