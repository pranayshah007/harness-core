/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.index.migrator;

import static dev.morphia.aggregation.Accumulator.accumulator;
import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Group.id;

import io.harness.delegate.beans.DelegateScope;
import io.harness.delegate.beans.DelegateScope.DelegateScopeKeys;
import io.harness.persistence.HIterator;

import dev.morphia.AdvancedDatastore;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateScopeNameUniqueInAccountMigration implements Migrator {
  @Override
  public void execute(AdvancedDatastore datastore) {
    log.info("Starting migration of delegate scopes with duplicate names for accountId.");
    Query<AggregateResult> queryForMultipleItems =
        datastore.createQuery(AggregateResult.class).field("count").greaterThan(1);
    AggregationPipeline invalidEntryPipeline =
        datastore.createAggregation(DelegateScope.class)
            .group(id(grouping("accountId", "accountId"), grouping("name", "name")),
                grouping("count", accumulator("$sum", 1)))
            .match(queryForMultipleItems);

    try (HIterator<AggregateResult> invalidEntries =
             new HIterator((MorphiaIterator) invalidEntryPipeline.out(AggregateResult.class))) {
      for (AggregateResult invalidEntry : invalidEntries) {
        Query<DelegateScope> delegateScopeToRenameQuery = datastore.createQuery(DelegateScope.class)
                                                              .field(DelegateScopeKeys.accountId)
                                                              .equal(invalidEntry.get_id().getAccountId())
                                                              .field(DelegateScopeKeys.name)
                                                              .equal(invalidEntry.get_id().getName());
        try (HIterator<DelegateScope> delegateScopesToRename = new HIterator<>(delegateScopeToRenameQuery.fetch())) {
          int index = 1;
          for (DelegateScope delegateScope : delegateScopeToRenameQuery) {
            updateDelegateScope(datastore, index++, delegateScope);
          }
        }
      }
    }
    log.info("Finished migration of delegate scopes with duplicate names for accountId.");
  }

  private void updateDelegateScope(AdvancedDatastore datastore, int index, DelegateScope delegateScope) {
    try {
      log.info("Updating delegate scope.");
      Query<DelegateScope> updateQuery =
          datastore.createQuery(DelegateScope.class).field(DelegateScopeKeys.uuid).equal(delegateScope.getUuid());
      UpdateOperations<DelegateScope> updateOperations =
          datastore.createUpdateOperations(DelegateScope.class)
              .set(DelegateScopeKeys.name, delegateScope.getName() + "_" + index);
      datastore.findAndModify(updateQuery, updateOperations, new FindAndModifyOptions());
      log.info("Delegate scope updated successfully.");
    } catch (Exception e) {
      log.error("Unexpected error occurred while processing delegate scope.", e);
    }
  }
}
