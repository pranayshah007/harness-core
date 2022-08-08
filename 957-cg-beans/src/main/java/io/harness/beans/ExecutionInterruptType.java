/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * Describes different possible events for state.
 *
 * @author Rishi
 */
@OwnedBy(HarnessTeam.CDC)
public enum ExecutionInterruptType {
  /**
   * Abort state event.
   */
  ABORT("Abort execution of the current node"),
  /**
   * Abort all state event.
   */
  ABORT_ALL("Abort execution of all nodes for the current workflow"),
  /**
   * Pause state event.
   */
  PAUSE("Pause execution of the current node"),
  /**
   * Pause state event as state needs more inputs from User.
   */
  PAUSE_FOR_INPUTS("Pause execution of the current node"),
  /**
   * Pause all state event.
   */
  PAUSE_ALL("Pause execution of all nodes for the current workflow"),
  /**
   * Resume state event.
   */
  RESUME("Resume execution of the paused node"),
  /**
   * Resume all state event.
   */
  RESUME_ALL("Resume execution of all paused nodes in the current workflow"),
  /**
   * Retry state event.
   */
  RETRY("Retry the node execution"),
  /**
   * Ignore state event.
   */
  IGNORE("Ignore error and go to next"),
  /**
   * Waiting for Manual Intervention.
   */
  WAITING_FOR_MANUAL_INTERVENTION("Waiting for manual intervention on the current node failure"),
  /**
   * Mark as failed.
   */
  MARK_FAILED("Mark the node as failed"),
  /**
   * Mark as success.
   */

  MARK_SUCCESS("Mark the node as success"),

  ROLLBACK("Rollback"),

  ROLLBACK_PROVISIONER_AFTER_PHASES("Rollback Provisioner after Phases"),

  NEXT_STEP("Next Step"),

  END_EXECUTION("End Execution"),

  ROLLBACK_DONE("Rollback Done"),

  MARK_EXPIRED("Mark the node as expired"),

  CONTINUE_WITH_DEFAULTS("Run the same execution Instance with default values"),

  CONTINUE_PIPELINE_STAGE("Run the env state with runtime values"),
  ROLLBACK_ON_APPROVAL("Rollback on approval"),
  ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL("Rollback Provisioner after Phases on approval");

  private String description;

  ExecutionInterruptType(String description) {
    this.description = description;
  }

  /**
   * Gets description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }
}
