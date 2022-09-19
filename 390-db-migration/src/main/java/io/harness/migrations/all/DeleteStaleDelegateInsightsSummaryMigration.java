/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.persistence.HPersistence;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.DBCollection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeleteStaleDelegateInsightsSummaryMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    DBCollection delegateInsightsSummary =
        wingsPersistence.getDatastore(HPersistence.DEFAULT_STORE).getDB().getCollection("delegateInsightsSummary");
    delegateInsightsSummary.drop();
    log.info("Complete");
  }
}
