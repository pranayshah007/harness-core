/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * The Enum CommandExecutionStatus.
 */
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.logging.CommandExecutionStatus")
public enum CommandExecutionStatus {
  /**
   * Success execution status.
   */
  SUCCESS(UnitStatus.SUCCESS),
  /**
   * Failure execution status.
   */
  FAILURE(UnitStatus.FAILURE),
  /**
   * Running execution status.
   */
  RUNNING(UnitStatus.RUNNING),

  /**
   * Queued execution status.
   */
  QUEUED(UnitStatus.QUEUED),

  /*
   *  Skipped execution status
   * */
  SKIPPED(UnitStatus.SKIPPED);

  private UnitStatus unitStatus;

  CommandExecutionStatus(UnitStatus unitStatus) {
    this.unitStatus = unitStatus;
  }

  public static boolean isTerminalStatus(CommandExecutionStatus commandExecutionStatus) {
    return commandExecutionStatus == SUCCESS || commandExecutionStatus == FAILURE;
  }

  public UnitStatus getUnitStatus() {
    return unitStatus;
  }

  public static CommandExecutionStatus getCommandExecutionStatus(UnitStatus unitStatus) {
    for (CommandExecutionStatus commandExecutionStatus : CommandExecutionStatus.values()) {
      if (commandExecutionStatus.getUnitStatus().equals(unitStatus)) {
        return commandExecutionStatus;
      }
    }

    return null;
  }
}
