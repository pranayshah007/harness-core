/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;

import static software.wings.utils.Utils.uuidToIdentifier;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery;

import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateGroupIdentifierMigration implements Migration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    log.info("Starting the migration of the delegate groups identifiers.");
    Query<DelegateGroup> groupsQuery = persistence.createQuery(DelegateGroup.class, HQuery.excludeAuthority);
    try (HIterator<DelegateGroup> delegateGroups = new HIterator<>(groupsQuery.fetch())) {
      for (DelegateGroup group : delegateGroups) {
        log.debug("Migrating delegate group with uuid: ", group.getUuid());
        updateDelegateGroup(group);
      }
    }
    log.info("The migration for the delegates updated identifier field with normalized uuid.");
  }

  private void updateDelegateGroup(DelegateGroup group) {
    try {
      log.info("Updating delegate group.");
      Query<DelegateGroup> groupQuery = persistence.createQuery(DelegateGroup.class)
                                            .filter(DelegateGroupKeys.uuid, group.getUuid())
                                            .filter(DelegateGroupKeys.accountId, group.getAccountId());
      UpdateOperations<DelegateGroup> updateOperations =
          persistence.createUpdateOperations(DelegateGroup.class)
              .set(DelegateGroupKeys.identifier, uuidToIdentifier(group.getUuid()));

      persistence.findAndModify(groupQuery, updateOperations, new FindAndModifyOptions());
      log.info("Delegate group updated successfully.");
    } catch (Exception ex) {
      log.error("Unexpected error occurred while migrating delegate group.", ex);
    }
  }
}
