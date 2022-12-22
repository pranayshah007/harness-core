/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.migrations.Migration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class AddValidUntilToDelegateGroupMigration implements Migration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    log.info("Starting migration to add valid until to delegate groups.");
    try {
      Query<DelegateGroup> delegateGroupQuery = persistence.createQuery(DelegateGroup.class, excludeAuthority)
                                                    .field(DelegateGroupKeys.validUntil)
                                                    .doesNotExist();
      UpdateOperations<DelegateGroup> updateOperations =
          persistence.createUpdateOperations(DelegateGroup.class)
              .set(DelegateGroupKeys.validUntil,
                  Date.from(OffsetDateTime.now().plusDays(DelegateGroup.TTL.toDays()).toInstant()));
      UpdateResults updateResults = persistence.update(delegateGroupQuery, updateOperations);
      log.info("Updated {} records", updateResults.getUpdatedCount());
    } catch (Exception e) {
      log.info("Exception occurred during running migration to add valid until to delegate group.", e);
    }
    log.info("Migration completed.");
  }
}
