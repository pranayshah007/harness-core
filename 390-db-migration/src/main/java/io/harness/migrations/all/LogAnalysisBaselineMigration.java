/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.service.impl.WorkflowExecutionBaselineServiceImpl.BASELINE_TTL;

import static java.time.Duration.ofMillis;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.baseline.WorkflowExecutionBaseline;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogMLAnalysisRecord.LogMLAnalysisRecordKeys;

import com.google.inject.Inject;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogAnalysisBaselineMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Override
  public void migrate() {
    try (HIterator<WorkflowExecutionBaseline> iterator =
             new HIterator<>(wingsPersistence.createQuery(WorkflowExecutionBaseline.class, excludeAuthority).fetch())) {
      while (iterator.hasNext()) {
        final WorkflowExecutionBaseline workflowExecutionBaseline = iterator.next();

        final UpdateResults updateResults =
            wingsPersistence.update(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
                                        .filter(LogMLAnalysisRecordKeys.workflowExecutionId,
                                            workflowExecutionBaseline.getWorkflowExecutionId()),
                wingsPersistence.createUpdateOperations(LogMLAnalysisRecord.class)
                    .set(LogMLAnalysisRecordKeys.validUntil, BASELINE_TTL));

        if (updateResults.getUpdatedCount() == 0) {
          log.info(
              "for pinned baseline {} there was no analysis remaining in the db", workflowExecutionBaseline.getUuid());
        } else {
          log.info("updated {} records for baseline {}", updateResults.getUpdatedCount(),
              workflowExecutionBaseline.getUuid());
        }
        sleep(ofMillis(1000));
      }
    }
  }
}
