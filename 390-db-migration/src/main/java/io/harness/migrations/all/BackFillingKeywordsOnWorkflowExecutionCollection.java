/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.ExecutionArgs;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.SPG)
public class BackFillingKeywordsOnWorkflowExecutionCollection implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  private static final String DEBUG_MESSAGE = "BACKFILLING_WFE_MIGRATION: ";
  private static final String ACCOUNT_ID = "78iEHpolS_uhk2XmjVO-4Q";
  private static Duration FOUR_MONTHS = Duration.ofDays(30 * 4);
  private static Long topMillisToBeConsidered = 1679596147000L;
  private static Long bottomMillisToBeConsidered =
      Duration.ofMillis(topMillisToBeConsidered).minus(FOUR_MONTHS).toMillis();

  private void runMigration() {
    BulkWriteOperation bulkWriteOperation =
        wingsPersistence.getCollection(WorkflowExecution.class).initializeUnorderedBulkOperation();
    long count = 0;
    try (HIterator<WorkflowExecution> workflowExecutionHIterator =
             new HIterator<>(wingsPersistence.createQuery(WorkflowExecution.class)
                                 .filter(WorkflowExecutionKeys.accountId, ACCOUNT_ID)
                                 .field(WorkflowExecutionKeys.createdAt)
                                 .greaterThan(bottomMillisToBeConsidered)
                                 .field(WorkflowExecutionKeys.createdAt)
                                 .lessThan(topMillisToBeConsidered)
                                 .project(WorkflowExecutionKeys.accountId, true)
                                 .project(WorkflowExecutionKeys.createdAt, true)
                                 .project(WorkflowExecutionKeys.executionArgs, true)
                                 .project(WorkflowExecutionKeys.keywords, true)
                                 .fetch())) {
      if (!workflowExecutionHIterator.hasNext()) {
        return;
      }
      while (workflowExecutionHIterator.hasNext()) {
        WorkflowExecution workflowExecution = workflowExecutionHIterator.next();
        try {
          Set<String> keywords = workflowExecution.getKeywords();
          ExecutionArgs executionArgs = workflowExecution.getExecutionArgs();
          if (executionArgs != null && executionArgs.getArtifacts() != null) {
            List<Artifact> artifacts = executionArgs.getArtifacts();
            for (Artifact artifact : artifacts) {
              ArtifactMetadata metadata = artifact.getMetadata();
              if (metadata != null) {
                String image = metadata.get("image");
                if (image != null && !keywords.contains(image)) {
                  count++;
                  keywords.add(image);
                  BasicDBObject basicDBObject = new BasicDBObject().append("_id", workflowExecution.getUuid());
                  BasicDBObject updateOps = new BasicDBObject(WorkflowExecutionKeys.keywords, keywords);
                  bulkWriteOperation.find(basicDBObject).update(new BasicDBObject("$set", updateOps));
                }
              }
            }
          }
        } catch (Exception e) {
          log.error(
              DEBUG_MESSAGE + "Migration failed for workflowExecution with id {} in accountId: 78iEHpolS_uhk2XmjVO",
              workflowExecution.getUuid(), e);
        }
      }
      if (count > 0) {
        bulkWriteOperation.execute();
      }
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error creating query", e);
    }
  }

  public void migrate() {
    log.info(DEBUG_MESSAGE + "Starting migration");
    runMigration();
    log.info(DEBUG_MESSAGE + "Completed migration");
  }
}
