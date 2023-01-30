/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.migrations.Migration;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddNgFieldToDelegateGroupMigration implements Migration {
  @Inject protected WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("adding ng field to delegate group");
    UpdateResults updateResults = wingsPersistence.update(
        wingsPersistence.createQuery(DelegateGroup.class, excludeAuthority).field(DelegateGroupKeys.ng).doesNotExist(),
        wingsPersistence.createUpdateOperations(DelegateGroup.class).set(DelegateGroupKeys.ng, false));
    log.info("updated {} records", updateResults.getUpdatedCount());
  }
}
