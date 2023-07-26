/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.Status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
@Schema(name = "ExecutionStatus", description = "This is the Execution Status of the entity")
public enum ExecutionStatus {
  @JsonProperty("Running") RUNNING(Status.RUNNING, "Running"),
  @JsonProperty("AsyncWaiting") ASYNCWAITING(Status.ASYNC_WAITING, "AsyncWaiting"),
  @JsonProperty("TaskWaiting") TASKWAITING(Status.TASK_WAITING, "TaskWaiting"),
  @JsonProperty("TimedWaiting") TIMEDWAITING(Status.TIMED_WAITING, "TimedWaiting"),
  @JsonProperty("Failed") FAILED(Status.FAILED, "Failed"),
  @JsonProperty("Errored") ERRORED(Status.ERRORED, "Errored"),
  @JsonProperty("IgnoreFailed") IGNOREFAILED(Status.IGNORE_FAILED, "IgnoreFailed"),
  @JsonProperty("NotStarted") NOTSTARTED(null, "NotStarted"),
  @JsonProperty("Expired") EXPIRED(Status.EXPIRED, "Expired"),
  @JsonProperty("Aborted") ABORTED(Status.ABORTED, "Aborted"),
  @JsonProperty("Discontinuing") DISCONTINUING(Status.DISCONTINUING, "Discontinuing"),
  @JsonProperty("Queued") QUEUED(Status.QUEUED, "Queued"),
  @JsonProperty("Paused") PAUSED(Status.PAUSED, "Paused"),
  @JsonProperty("ResourceWaiting") RESOURCEWAITING(Status.RESOURCE_WAITING, "ResourceWaiting"),
  @JsonProperty("InterventionWaiting") INTERVENTIONWAITING(Status.INTERVENTION_WAITING, "InterventionWaiting"),
  @JsonProperty("ApprovalWaiting") APPROVALWAITING(Status.APPROVAL_WAITING, "ApprovalWaiting"),
  @JsonProperty("WaitStepRunning") WAITSTEPRUNNING(Status.WAIT_STEP_RUNNING, "WaitStepRunning"),
  @JsonProperty("QueuedLicenseLimitReached")
  QUEUED_LICENSE_LIMIT_REACHED(Status.QUEUED_LICENSE_LIMIT_REACHED, "QueuedLicenseLimitReached"),
  @JsonProperty("QueuedExecutionConcurrencyReached")
  QUEUED_EXECUTION_CONCURRENCY_REACHED(
      Status.QUEUED_EXECUTION_CONCURRENCY_REACHED, "QueuedExecutionConcurrencyReached"),
  @JsonProperty("Success") SUCCESS(Status.SUCCEEDED, "Success"),
  @JsonProperty("Suspended") SUSPENDED(Status.SUSPENDED, "Suspended"),
  @JsonProperty("Skipped") SKIPPED(Status.SKIPPED, "Skipped"),
  @JsonProperty("Pausing") PAUSING(Status.PAUSING, "Pausing"),
  @JsonProperty("ApprovalRejected") APPROVALREJECTED(Status.APPROVAL_REJECTED, "ApprovalRejected"),
  @JsonProperty("InputWaiting") INPUTWAITING(Status.INPUT_WAITING, "InputWaiting"),
  @JsonProperty("AbortedByFreeze") ABORTEDBYFREEZE(Status.FREEZE_FAILED, "AbortedByFreeze"),

  //@JsonIgnore added to not show older enums till migration is written to change their instances to new enums in DB.
  @JsonIgnore NOT_STARTED(null, true),
  @JsonIgnore INTERVENTION_WAITING(Status.INTERVENTION_WAITING, true),
  @JsonIgnore APPROVAL_WAITING(Status.APPROVAL_WAITING, true),
  @JsonIgnore APPROVAL_REJECTED(Status.APPROVAL_REJECTED, true),
  @JsonIgnore WAITING(Status.RESOURCE_WAITING, "Waiting", true);

  Status engineStatus;
  String displayName;
  boolean ignoreStatus;

  ExecutionStatus(Status engineStatus, String displayName) {
    this.engineStatus = engineStatus;
    this.displayName = displayName;
  }

  // Made for JsonIgnore enums. To be removed once migration code is written.
  ExecutionStatus(Status engineStatus, boolean ignoreStatus) {
    this.engineStatus = engineStatus;
    this.ignoreStatus = ignoreStatus;
  }

  // Made for JsonIgnore enums. To be removed once migration code is written.
  ExecutionStatus(Status engineStatus, String displayName, boolean ignoreStatus) {
    this.engineStatus = engineStatus;
    this.displayName = displayName;
    this.ignoreStatus = ignoreStatus;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ExecutionStatus getExecutionStatus(@JsonProperty("type") String displayName) {
    for (ExecutionStatus executionStatus : ExecutionStatus.values()) {
      if (executionStatus.displayName.equalsIgnoreCase(displayName)) {
        return executionStatus;
      }
    }
    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", displayName, Arrays.toString(ExecutionStatus.values())));
  }

  public static ExecutionStatus getExecutionStatus(Status status) {
    for (ExecutionStatus executionStatus : ExecutionStatus.values()) {
      if (executionStatus.engineStatus == status && !executionStatus.ignoreStatus) {
        return executionStatus;
      }
    }
    throw new IllegalArgumentException(String.format("No Execution Status mapper found for input status: %s", status));
  }

  public static List<ExecutionStatus> getListExecutionStatus(EnumSet<Status> statusList) {
    return statusList.stream().map(ExecutionStatus::getExecutionStatus).collect(Collectors.toList());
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  public Status getEngineStatus() {
    return engineStatus;
  }
}
