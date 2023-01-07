/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.governance.GovernanceConfig.GovernanceConfigKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CDC)
public class AddEnableIteratorsToGovernanceConfig implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  private static final String LOG_IDENTIFIER = "[DEPLOYMENT_FREEZE_ITERATOR_OPTIMIZATION_MIGRATION]: ";

  @Override
  public void migrate() {
    log.info(LOG_IDENTIFIER + "Running migration to add enableNextIterations to governance Config ");
    int count = 0;
    try (HIterator<GovernanceConfig> governanceConfigHIterator =
             new HIterator<>(wingsPersistence.createQuery(GovernanceConfig.class)
                                 .field(GovernanceConfigKeys.accountId)
                                 .exists()
                                 .fetch())) {
      while (governanceConfigHIterator.hasNext()) {
        GovernanceConfig governanceConfig = governanceConfigHIterator.next();
        log.info(LOG_IDENTIFIER + "Updating governanceConfig with id {} for accountId {}", governanceConfig.getUuid(),
            governanceConfig.getAccountId());
        Query<GovernanceConfig> query = wingsPersistence.createQuery(GovernanceConfig.class)
                                            .filter(GovernanceConfigKeys.accountId, governanceConfig.getAccountId());
        UpdateOperations<GovernanceConfig> operations = wingsPersistence.createUpdateOperations(GovernanceConfig.class);
        setUnset(operations, GovernanceConfigKeys.enableNextIterations,
            EmptyPredicate.isNotEmpty(governanceConfig.getNextIterations()));
        setUnset(operations, GovernanceConfigKeys.enableNextCloseIterations,
            EmptyPredicate.isNotEmpty(governanceConfig.getNextCloseIterations()));
        wingsPersistence.findAndModify(query, operations, WingsPersistence.returnNewOptions);
        count++;
        log.info(LOG_IDENTIFIER + "Updated governanceConfig with id {} for accountId {}", governanceConfig.getUuid(),
            governanceConfig.getAccountId());
      }
    } catch (Exception e) {
      log.error(LOG_IDENTIFIER + "Could not run migration to add enableNextIterations to governance Config ", e);
    }
    log.info(LOG_IDENTIFIER + "Updated {} governanceConfig records", count);
  }
}
