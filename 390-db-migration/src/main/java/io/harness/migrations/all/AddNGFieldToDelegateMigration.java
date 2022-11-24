/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.migrations.Migration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class AddNGFieldToDelegateMigration implements Migration {
  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    log.info("Starting migration to set ng flag as false for delegates not having ng field in db.");
    try {
      Query<Delegate> delegateQuery =
          persistence.createQuery(Delegate.class, excludeAuthority).field(DelegateKeys.ng).doesNotExist();
      UpdateOperations<Delegate> updateOperations =
          persistence.createUpdateOperations(Delegate.class).set(DelegateKeys.ng, false);
      UpdateResults updateResults = persistence.update(delegateQuery, updateOperations);
      log.info("Updated {} records", updateResults.getUpdatedCount());
    } catch (Exception e) {
      log.info(
          "Exception occurred during running migration to set ng flag as false for delegates not having ng field in db.");
    }
    log.info("Migration completed.");
  }
}
