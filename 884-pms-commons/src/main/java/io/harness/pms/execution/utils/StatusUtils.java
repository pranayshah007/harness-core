/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.APPROVAL_REJECTED;
import static io.harness.pms.contracts.execution.Status.APPROVAL_WAITING;
import static io.harness.pms.contracts.execution.Status.ASYNC_WAITING;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.EXPIRED;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.IGNORE_FAILED;
import static io.harness.pms.contracts.execution.Status.INPUT_WAITING;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.contracts.execution.Status.PAUSED;
import static io.harness.pms.contracts.execution.Status.PAUSING;
import static io.harness.pms.contracts.execution.Status.QUEUED;
import static io.harness.pms.contracts.execution.Status.RESOURCE_WAITING;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.contracts.execution.Status.SKIPPED;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.pms.contracts.execution.Status.SUSPENDED;
import static io.harness.pms.contracts.execution.Status.TASK_WAITING;
import static io.harness.pms.contracts.execution.Status.TIMED_WAITING;
import static io.harness.pms.contracts.execution.Status.UNRECOGNIZED;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.Status;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class StatusUtils {
  // Status Groups
  private final EnumSet<Status> FINALIZABLE_STATUSES = EnumSet.of(QUEUED, RUNNING, PAUSING, PAUSED, ASYNC_WAITING,
      APPROVAL_WAITING, RESOURCE_WAITING, INTERVENTION_WAITING, TASK_WAITING, TIMED_WAITING, DISCONTINUING, PAUSING);

  private final EnumSet<Status> ABORT_AND_EXPIRE_STATUSES = EnumSet.of(RUNNING, PAUSING, PAUSED, ASYNC_WAITING,
      APPROVAL_WAITING, RESOURCE_WAITING, INTERVENTION_WAITING, TASK_WAITING, TIMED_WAITING, DISCONTINUING, PAUSING);

  private final EnumSet<Status> POSITIVE_STATUSES = EnumSet.of(SUCCEEDED, SKIPPED, SUSPENDED, IGNORE_FAILED);

  private final EnumSet<Status> BROKE_STATUSES = EnumSet.of(FAILED, ERRORED, EXPIRED, APPROVAL_REJECTED);

  private final EnumSet<Status> BROKE_AND_ABORTED_STATUSES =
      EnumSet.of(FAILED, ERRORED, EXPIRED, APPROVAL_REJECTED, ABORTED);

  private final EnumSet<Status> RESUMABLE_STATUSES = EnumSet.of(QUEUED, RUNNING, ASYNC_WAITING, APPROVAL_WAITING,
      RESOURCE_WAITING, TASK_WAITING, TIMED_WAITING, INTERVENTION_WAITING);

  private final EnumSet<Status> FLOWING_STATUSES =
      EnumSet.of(RUNNING, ASYNC_WAITING, TASK_WAITING, TIMED_WAITING, DISCONTINUING);

  private final EnumSet<Status> ACTIVE_STATUSES = EnumSet.of(RUNNING, INTERVENTION_WAITING, APPROVAL_WAITING,
      RESOURCE_WAITING, ASYNC_WAITING, TASK_WAITING, TIMED_WAITING, DISCONTINUING);

  private final EnumSet<Status> UNPAUSABLE_CHILD_STATUSES = EnumSet.of(
      RUNNING, INTERVENTION_WAITING, APPROVAL_WAITING, ASYNC_WAITING, TASK_WAITING, TIMED_WAITING, DISCONTINUING);

  private final EnumSet<Status> FINAL_STATUSES =
      EnumSet.of(SKIPPED, IGNORE_FAILED, ABORTED, ERRORED, FAILED, EXPIRED, SUSPENDED, SUCCEEDED, APPROVAL_REJECTED);

  private final EnumSet<Status> GRAPH_UPDATE_STATUSES = EnumSet.of(RUNNING, INTERVENTION_WAITING, TIMED_WAITING,
      ASYNC_WAITING, TASK_WAITING, DISCONTINUING, PAUSING, QUEUED, PAUSED, APPROVAL_WAITING, RESOURCE_WAITING);

  private final EnumSet<Status> RETRYABLE_STATUSES =
      EnumSet.of(INTERVENTION_WAITING, FAILED, ERRORED, EXPIRED, APPROVAL_REJECTED);

  private final EnumSet<Status> RETRYABLE_FAILED_STATUSES = EnumSet.of(FAILED, EXPIRED, APPROVAL_REJECTED, ABORTED);

  public EnumSet<Status> finalizableStatuses() {
    return FINALIZABLE_STATUSES;
  }

  public EnumSet<Status> getRetryableFailedStatuses() {
    return RETRYABLE_FAILED_STATUSES;
  }

  public EnumSet<Status> positiveStatuses() {
    return POSITIVE_STATUSES;
  }

  public EnumSet<Status> brokeStatuses() {
    return BROKE_STATUSES;
  }

  public EnumSet<Status> brokeAndAbortedStatuses() {
    return BROKE_AND_ABORTED_STATUSES;
  }

  public EnumSet<Status> resumableStatuses() {
    return RESUMABLE_STATUSES;
  }

  public EnumSet<Status> flowingStatuses() {
    return FLOWING_STATUSES;
  }

  public EnumSet<Status> retryableStatuses() {
    return RETRYABLE_STATUSES;
  }

  public EnumSet<Status> finalStatuses() {
    return FINAL_STATUSES;
  }

  public EnumSet<Status> unpausableChildStatuses() {
    return UNPAUSABLE_CHILD_STATUSES;
  }

  public EnumSet<Status> activeStatuses() {
    return ACTIVE_STATUSES;
  }

  public EnumSet<Status> graphUpdateStatuses() {
    return GRAPH_UPDATE_STATUSES;
  }

  public EnumSet<Status> abortAndExpireStatuses() {
    return ABORT_AND_EXPIRE_STATUSES;
  }

  public EnumSet<Status> nodeAllowedStartSet(Status status) {
    switch (status) {
      case RUNNING:
        return EnumSet.of(QUEUED, ASYNC_WAITING, APPROVAL_WAITING, RESOURCE_WAITING, TASK_WAITING, TIMED_WAITING,
            INTERVENTION_WAITING, PAUSED, PAUSING, APPROVAL_REJECTED, INPUT_WAITING);
      case INTERVENTION_WAITING:
        return BROKE_STATUSES;
      case TIMED_WAITING:
      case ASYNC_WAITING:
      case APPROVAL_WAITING:
      case RESOURCE_WAITING:
      case TASK_WAITING:
      case PAUSING:
      case SKIPPED:
      case INPUT_WAITING:
        return EnumSet.of(QUEUED, RUNNING);
      case PAUSED:
        return EnumSet.of(QUEUED, RUNNING, PAUSING);
      case DISCONTINUING:
        return EnumSet.of(QUEUED, RUNNING, INTERVENTION_WAITING, TIMED_WAITING, ASYNC_WAITING, TASK_WAITING, PAUSING,
            RESOURCE_WAITING, APPROVAL_WAITING, QUEUED, PAUSED, FAILED, SUSPENDED, EXPIRED, APPROVAL_REJECTED);
      case QUEUED:
        return EnumSet.of(PAUSED, PAUSING);
      case ABORTED:
      case ERRORED:
      case SUSPENDED:
      case FAILED:
      case EXPIRED:
      case APPROVAL_REJECTED:
        return FINALIZABLE_STATUSES;
      case SUCCEEDED:
        return EnumSet.of(INTERVENTION_WAITING, RUNNING, QUEUED);
      case IGNORE_FAILED:
        return EnumSet.of(EXPIRED, FAILED, INTERVENTION_WAITING, RUNNING, APPROVAL_REJECTED);
      default:
        throw new IllegalStateException("Unexpected value: " + status);
    }
  }

  public EnumSet<Status> planAllowedStartSet(Status status) {
    switch (status) {
      case INTERVENTION_WAITING:
        return EnumSet.of(RUNNING, PAUSING, PAUSED);
      case PAUSED:
        return EnumSet.of(QUEUED, RUNNING, PAUSING, INTERVENTION_WAITING);
      case SUCCEEDED:
        return EnumSet.of(PAUSING, INTERVENTION_WAITING, RUNNING, APPROVAL_WAITING);
      default:
        return nodeAllowedStartSet(status);
    }
  }

  public boolean isFinalStatus(Status status) {
    if (status == null) {
      return false;
    }
    return FINAL_STATUSES.contains(status);
  }

  public Status calculateStatus(List<Status> statuses, String planExecutionId) {
    Status status = calculateStatus(statuses);
    if (status == UNRECOGNIZED) {
      log.error("Cannot calculate the end status for PlanExecutionId : {}", planExecutionId);
      return ERRORED;
    }
    return status;
  }

  public Status calculateStatusForNode(List<Status> statuses, String nodeExecutionId) {
    Status status = calculateStatus(statuses);
    if (status == UNRECOGNIZED) {
      log.error("Cannot calculate the end status for NodeExecutionId : {}", nodeExecutionId);
      return ERRORED;
    }
    return status;
  }

  // DO NOT Change order of the switch cases
  private Status calculateStatus(List<Status> statuses) {
    if (StatusUtils.positiveStatuses().containsAll(statuses)
        && statuses.stream().anyMatch(status -> status == IGNORE_FAILED)) {
      return IGNORE_FAILED;
    } else if (StatusUtils.positiveStatuses().containsAll(statuses)) {
      return SUCCEEDED;
    } else if (statuses.stream().anyMatch(status -> status == ABORTED)) {
      return ABORTED;
    } else if (statuses.stream().anyMatch(status -> status == ERRORED)) {
      return ERRORED;
    } else if (statuses.stream().anyMatch(status -> status == FAILED)) {
      return FAILED;
    } else if (statuses.stream().anyMatch(status -> status == APPROVAL_REJECTED)) {
      return APPROVAL_REJECTED;
    } else if (statuses.stream().anyMatch(status -> status == EXPIRED)) {
      return EXPIRED;
    } else if (statuses.stream().anyMatch(status -> status == INTERVENTION_WAITING)) {
      return INTERVENTION_WAITING;
    } else if (statuses.stream().anyMatch(status -> status == APPROVAL_WAITING)) {
      return APPROVAL_WAITING;
    } else if (statuses.stream().anyMatch(status -> status == RESOURCE_WAITING)) {
      return RESOURCE_WAITING;
    } else if (statuses.stream().anyMatch(status -> status == QUEUED)) {
      return QUEUED;
    } else if (!Collections.disjoint(statuses, FLOWING_STATUSES)) {
      return RUNNING;
    } else if (statuses.stream().anyMatch(status -> status == PAUSED)) {
      return PAUSED;
    } else {
      return UNRECOGNIZED;
    }
  }
}
