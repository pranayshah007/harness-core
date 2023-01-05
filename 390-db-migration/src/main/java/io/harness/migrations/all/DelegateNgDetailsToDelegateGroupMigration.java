/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateNgDetailsToDelegateGroupMigration implements Migration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    log.info("Starting the migration of the Ng delegate details to delegate groups.");

    Query<Delegate> query = persistence.createQuery(Delegate.class, excludeAuthority)
                                .field(DelegateKeys.delegateGroupId)
                                .exists()
                                .field(DelegateKeys.delegateGroupName)
                                .doesNotExist();

    try (HIterator<Delegate> records = new HIterator<>(query.fetch())) {
      for (Delegate delegate : records) {
        try {
          Query<DelegateGroup> groupQuery = this.persistence.createQuery(DelegateGroup.class, excludeAuthority)
                                                .filter(DelegateGroupKeys.uuid, delegate.getDelegateGroupId());

          UpdateOperations<DelegateGroup> updateOperations =
              this.persistence.createUpdateOperations(DelegateGroup.class);
          setUnset(updateOperations, DelegateGroupKeys.ng, true);
          setUnset(updateOperations, DelegateGroupKeys.owner, delegate.getOwner());
          setUnset(updateOperations, DelegateGroupKeys.delegateType, KUBERNETES);
          setUnset(updateOperations, DelegateGroupKeys.description, delegate.getDescription());
          setUnset(updateOperations, DelegateGroupKeys.delegateConfigurationId, delegate.getDelegateProfileId());
          setUnset(updateOperations, DelegateGroupKeys.sizeDetails, delegate.getSizeDetails());

          persistence.findAndModify(groupQuery, updateOperations, HPersistence.returnNewOptions);
        } catch (Exception ex) {
          log.error("Unexpected error occurred while copying ng data from delegate {}", delegate.getUuid());
        }
      }
    }

    log.info("The migration of the Ng delegate details to delegate groups has finished.");
  }
}
