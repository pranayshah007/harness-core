/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.verification.CVConfiguration;

import com.google.inject.Inject;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class LogAnalysisDeprecatedRecordMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try (HIterator<CVConfiguration> iterator =
             new HIterator<>(wingsPersistence.createQuery(CVConfiguration.class, excludeAuthority).fetch())) {
      for (CVConfiguration cvConfiguration : iterator) {
        log.info("migrating for {}", cvConfiguration.getUuid());
        final UpdateResults updateResults =
            wingsPersistence.update(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
                                        .filter("cvConfigId", cvConfiguration.getUuid())
                                        .field("deprecated")
                                        .doesNotExist(),
                wingsPersistence.createUpdateOperations(LogMLAnalysisRecord.class).set("deprecated", false));
        log.info("updated {} records", updateResults.getUpdatedCount());
      }
    }

    log.info("migration done...");
  }
}
