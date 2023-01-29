/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegatesWithoutGroupMigration implements Migration {
  @Inject private HPersistence persistence;
  @Inject private DelegateService delegateService;

  @Override
  public void migrate() {
    log.info("Starting the migration of the delegates without an assigned delegate group.");
    Query<Delegate> delegatesQuery = persistence.createQuery(Delegate.class)
                                         .field(DelegateKeys.delegateGroupName)
                                         .exists()
                                         .field(DelegateKeys.delegateGroupId)
                                         .doesNotExist();

    try (HIterator<Delegate> delegatesWithoutGroup = new HIterator<>(delegatesQuery.fetch())) {
      for (Delegate delegate : delegatesWithoutGroup) {
        log.debug("Delegate with ID {} has no delegate group assigned.", delegate.getUuid());
        DelegateGroup delegateGroup =
            delegateService.upsertDelegateGroup(delegate.getDelegateGroupName(), delegate.getAccountId(), null);

        log.debug("Assigning group with name: {} and id: {} for account: {} to delegate: {}", delegateGroup.getName(),
            delegateGroup.getUuid(), delegateGroup.getAccountId(), delegate.getUuid());
        updateGroupIdOnDelegate(delegate.getUuid(), delegate.getAccountId(), delegateGroup.getUuid());
      }
    }
    log.info("The migration for the delegates with no delegate group assigned has finished.");
  }

  private void updateGroupIdOnDelegate(String delegateId, String accountId, String delegateGroupId) {
    Query<Delegate> delegateQuery = persistence.createQuery(Delegate.class)
                                        .filter(DelegateKeys.uuid, delegateId)
                                        .filter(DelegateKeys.accountId, accountId);

    UpdateOperations<Delegate> updateOperations =
        persistence.createUpdateOperations(Delegate.class).set(DelegateKeys.delegateGroupId, delegateGroupId);
    persistence.findAndModify(delegateQuery, updateOperations, new FindAndModifyOptions());
  }
}
