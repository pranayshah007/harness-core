/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.migrations.Migration;
import io.harness.time.Timestamp;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogMLAnalysisRecord.LogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisStatus;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddAnalysisStatusMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Override
  public void migrate() {
    long startTime = Timestamp.currentMinuteBoundary() - TimeUnit.DAYS.toMillis(7);

    Query<LogMLAnalysisRecord> logDataRecordQuery = wingsPersistence.createQuery(LogMLAnalysisRecord.class)
                                                        .field("createdAt")
                                                        .greaterThan(startTime)
                                                        .field(LogMLAnalysisRecordKeys.analysisStatus)
                                                        .doesNotExist();

    UpdateOperations<LogMLAnalysisRecord> op = wingsPersistence.createUpdateOperations(LogMLAnalysisRecord.class);
    setUnset(op, LogMLAnalysisRecordKeys.analysisStatus, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    UpdateResults results = wingsPersistence.update(logDataRecordQuery, op);
    log.info("Updated {} logMLAnalysisRecords with analysisStatus", results.getUpdatedCount());
  }
}
