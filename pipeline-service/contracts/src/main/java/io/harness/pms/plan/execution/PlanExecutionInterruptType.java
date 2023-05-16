/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.interrupts.InterruptType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

// TODO: We need to adopt the interrupt type itself and remove this enum
@OwnedBy(HarnessTeam.PIPELINE)
@Deprecated
public enum PlanExecutionInterruptType {
  /**
   * Abort all state event.
   */
  @JsonProperty("AbortAll")
  ABORTALL("Abort execution of all nodes for the current workflow", InterruptType.ABORT_ALL, "AbortAll"),

  @JsonProperty("Abort") ABORT("Abort execution of given node for the current workflow", InterruptType.ABORT, "Abort"),
  /**
   * Pause all state event.
   */
  @JsonProperty("Pause")
  PAUSE("Pause execution of all nodes for the current workflow", InterruptType.PAUSE_ALL, "Pause"),
  /**
   * Resume all state event.
   */
  @JsonProperty("Resume")
  RESUME("Resume execution of all paused nodes in the current workflow", InterruptType.RESUME_ALL, "Resume"),

  @JsonProperty("Ignore") IGNORE("Ignore execution of  nodes in the current workflow", InterruptType.IGNORE, "Ignore"),

  @JsonProperty("StageRollback")
  STAGEROLLBACK("Do stage rollback of the execution", InterruptType.CUSTOM_FAILURE, "StageRollback"),

  @JsonProperty("StepGroupRollback")
  STEPGROUPROLLBACK("Do stage rollback of the execution", InterruptType.CUSTOM_FAILURE, "StepGroupRollback"),

  @JsonProperty("MarkAsSuccess")
  MARKASSUCCESS(
      "MarkSuccess execution of paused node in the current workflow", InterruptType.MARK_SUCCESS, "MarkAsSuccess"),

  @JsonProperty("ExpireAll") EXPIREALL("Expire Pipeline", InterruptType.EXPIRE_ALL, "ExpireAll"),

  @JsonProperty("Retry") RETRY("Retry execution of  paused node in the current workflow", InterruptType.RETRY, "Retry"),
  @JsonProperty("MarkAsFailure")
  MarkAsFailure(
      "MarkFailed execution of paused node in the current workflow", InterruptType.MARK_FAILED, "MarkAsFailure"),
  @JsonProperty("UserMarkedFailure")
  UserMarkedFailure(
      "Mark running steps/stages as user marked failure", InterruptType.USER_MARKED_FAIL_ALL, "UserMarkedFailure");

  private String description;
  private InterruptType executionInterruptType;
  private String displayName; // DO NOT CHANGE THESE, AS THEY ARE SAME AS FailureTypes.

  PlanExecutionInterruptType(String description, InterruptType executionInterruptType, String displayName) {
    this.description = description;
    this.executionInterruptType = executionInterruptType;
    this.displayName = displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static PlanExecutionInterruptType getPipelineExecutionInterrupt(@JsonProperty("type") String displayName) {
    for (PlanExecutionInterruptType PlanExecutionInterruptType : PlanExecutionInterruptType.values()) {
      if (PlanExecutionInterruptType.displayName.equalsIgnoreCase(displayName)) {
        return PlanExecutionInterruptType;
      }
    }
    throw new IllegalArgumentException(String.format("Invalid value:%s, the expected values are: %s", displayName,
        Arrays.toString(PlanExecutionInterruptType.values())));
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  public InterruptType getExecutionInterruptType() {
    return executionInterruptType;
  }
}
