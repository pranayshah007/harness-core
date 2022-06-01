/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import io.harness.logging.CommandExecutionStatus;

public class CleanupPowerShellCommandUnit extends AbstractCommandUnit {
  public static final String CLEANUP_POWERSHELL_UNIT_NAME = "Cleanup";

  public CleanupPowerShellCommandUnit() {
    super(CommandUnitType.EXEC);
    setName(CLEANUP_POWERSHELL_UNIT_NAME);
  }

  @Override
  public CommandExecutionStatus execute(CommandExecutionContext context) {
    return CommandExecutionStatus.SUCCESS;
  }
}
