/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStatus.COMPLETE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStatus.INCOMPLETE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.datadeletion.logcontext.DataDeletionStepLogContext;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionBucket;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStep;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionStepRecord;
import io.harness.logging.AutoLogContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
public abstract class DataDeletionHandler {
  DataDeletionBucket dataDeletionBucket;

  protected DataDeletionHandler(DataDeletionBucket dataDeletionBucket) {
    this.dataDeletionBucket = dataDeletionBucket;
  }

  public void executeSteps(DataDeletionRecord dataDeletionRecord) {
    // Filter out steps that will be executed
    List<DataDeletionStep> stepsToExecute = dataDeletionRecord.getRecords()
                                                .keySet()
                                                .stream()
                                                .filter(step -> isExecutableStep(step, dataDeletionRecord))
                                                .map(DataDeletionStep::valueOf)
                                                .collect(Collectors.toList());
    log.info("Steps to be executed: {}", stepsToExecute);

    // Order these steps based on prerequisites in the current bucket itself using topological sorting
    List<DataDeletionStep> orderedSteps = topologicalSort(stepsToExecute);
    log.info("Executing steps in order: {}", orderedSteps);

    // For each step in the topological order, call executeStep
    for (DataDeletionStep step : orderedSteps) {
      try (AutoLogContext ignore = new DataDeletionStepLogContext(step.name(), OVERRIDE_ERROR)) {
        DataDeletionStepRecord dataDeletionStepRecord = dataDeletionRecord.getRecords().get(step.name());
        if (checkPrerequisitesCompleted(step, dataDeletionRecord)) {
          if (executeStep(dataDeletionRecord, step)) {
            dataDeletionStepRecord.setStatus(COMPLETE);
          } else {
            dataDeletionStepRecord.setStatus(INCOMPLETE);
          }
          // update dataDeletionRecord
          dataDeletionStepRecord.setLastExecutedAt(Instant.now().toEpochMilli());
          dataDeletionStepRecord.setRetryCount(dataDeletionStepRecord.getRetryCount() + 1);
          dataDeletionRecord.setRetryCount(
              Math.max(dataDeletionRecord.getRetryCount(), dataDeletionStepRecord.getRetryCount()));
        }
      }
    }
  }

  public boolean executeStep(DataDeletionRecord dataDeletionRecord, DataDeletionStep dataDeletionStep) {
    log.info("Execute Step not implemented: dataDeletionStep: {}, dataDeletionBucket: {}", dataDeletionStep.name(),
        dataDeletionStep.getBucket().name());
    return false;
  }

  private boolean isExecutableStep(String step, DataDeletionRecord deletionRecord) {
    try {
      DataDeletionStep dataDeletionStep = DataDeletionStep.valueOf(step);
      return belongsToCurrentBucket(dataDeletionStep) && isNotCompleted(dataDeletionStep, deletionRecord)
          && checkPrerequisitesNotInCurrentBucketCompleted(dataDeletionStep, deletionRecord);
    } catch (IllegalArgumentException | NullPointerException e) {
      // This will happen in case of AUTOCUD and AUTOSTOPPING deletion steps
      return false;
    }
  }

  private boolean isNotCompleted(DataDeletionStep step, DataDeletionRecord deletionRecord) {
    return !deletionRecord.getRecords().containsKey(step.name())
        || !deletionRecord.getRecords().get(step.name()).getStatus().equals(COMPLETE);
  }

  private boolean belongsToCurrentBucket(DataDeletionStep step) {
    return step.getBucket().equals(this.dataDeletionBucket);
  }

  private boolean checkPrerequisitesCompleted(DataDeletionStep step, DataDeletionRecord deletionRecord) {
    List<DataDeletionStep> prerequisites = step.getPrerequisites();
    for (DataDeletionStep prerequisite : prerequisites) {
      if (!deletionRecord.getRecords().containsKey(prerequisite.name())
          || deletionRecord.getRecords().get(prerequisite.name()).getStatus().equals(INCOMPLETE)) {
        log.info("Pre-requisite step {} not completed for current step {}", prerequisite.name(), step.name());
        return false;
      }
    }
    return true;
  }

  private boolean checkPrerequisitesNotInCurrentBucketCompleted(
      DataDeletionStep step, DataDeletionRecord deletionRecord) {
    List<DataDeletionStep> prerequisites = step.getPrerequisites();
    for (DataDeletionStep prerequisite : prerequisites) {
      if (!belongsToCurrentBucket(prerequisite)
          && deletionRecord.getRecords().get(prerequisite.name()).getStatus().equals(INCOMPLETE)) {
        return false;
      }
    }
    return true;
  }

  private List<DataDeletionStep> topologicalSort(List<DataDeletionStep> steps) {
    List<DataDeletionStep> result = new ArrayList<>();
    Set<DataDeletionStep> visited = new HashSet<>();
    for (DataDeletionStep step : steps) {
      if (!visited.contains(step)) {
        topologicalSortDFS(step, visited, result);
      }
    }
    return result;
  }

  private void topologicalSortDFS(DataDeletionStep step, Set<DataDeletionStep> visited, List<DataDeletionStep> result) {
    visited.add(step);
    for (DataDeletionStep prerequisite : step.getPrerequisites()) {
      if (prerequisite.getBucket().equals(step.getBucket()) && !visited.contains(prerequisite)) {
        topologicalSortDFS(prerequisite, visited, result);
      }
    }
    result.add(step);
  }
}
