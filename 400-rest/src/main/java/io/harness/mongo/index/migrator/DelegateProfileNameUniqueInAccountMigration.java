/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.index.migrator;

import static dev.morphia.aggregation.Accumulator.accumulator;
import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Group.id;

import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfile.DelegateProfileKeys;
import io.harness.persistence.HIterator;

import dev.morphia.AdvancedDatastore;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateProfileNameUniqueInAccountMigration implements Migrator {
  @Override
  public void execute(AdvancedDatastore datastore) {
    log.info("Starting migration of delegate profiles with duplicate names for accountId.");
    Query<AggregateResult> queryForMultipleItems =
        datastore.createQuery(AggregateResult.class).field("count").greaterThan(1);
    AggregationPipeline invalidEntryPipeline =
        datastore.createAggregation(DelegateProfile.class)
            .group(id(grouping("accountId", "accountId"), grouping("name", "name")),
                grouping("count", accumulator("$sum", 1)))
            .match(queryForMultipleItems);

    try (HIterator<AggregateResult> invalidEntries =
             new HIterator((MorphiaIterator) invalidEntryPipeline.out(AggregateResult.class))) {
      for (AggregateResult invalidEntry : invalidEntries) {
        Query<DelegateProfile> delegateProfileToRenameQuery = datastore.createQuery(DelegateProfile.class)
                                                                  .field(DelegateProfileKeys.accountId)
                                                                  .equal(invalidEntry.get_id().getAccountId())
                                                                  .field(DelegateProfileKeys.name)
                                                                  .equal(invalidEntry.get_id().getName());
        try (HIterator<DelegateProfile> delegateProfilesToRename =
                 new HIterator<>(delegateProfileToRenameQuery.fetch())) {
          int index = 1;
          for (DelegateProfile delegateProfile : delegateProfilesToRename) {
            updateDelegateProfile(datastore, index++, delegateProfile);
          }
        }
      }
    }
    log.info("Finished migration of delegate profiles with duplicate names for accountId.");
  }

  private void updateDelegateProfile(AdvancedDatastore datastore, int index, DelegateProfile delegateProfile) {
    try {
      log.info("Updating delegate profile.");
      Query<DelegateProfile> updateQuery =
          datastore.createQuery(DelegateProfile.class).field(DelegateProfileKeys.uuid).equal(delegateProfile.getUuid());
      UpdateOperations<DelegateProfile> updateOperations =
          datastore.createUpdateOperations(DelegateProfile.class)
              .set(DelegateProfileKeys.name, delegateProfile.getName() + "_" + index);
      datastore.findAndModify(updateQuery, updateOperations, new FindAndModifyOptions());
      log.info("Delegate profile updated successfully.");
    } catch (Exception e) {
      log.error("Unexpected error occurred while processing delegate profile.", e);
    }
  }
}
