/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import io.harness.logging.CommandExecutionStatus;

import java.io.File;

/**
 * Created by peeyushaggarwal on 7/26/16.
 */
public class CleanupSshCommandUnit extends SshCommandUnit {
  /**
   * The constant CLEANUP_UNIT.
   */
  public static final String CLEANUP_UNIT = "Cleanup";

  /**
   * Instantiates a new Init command unit.
   */
  public CleanupSshCommandUnit() {
    super(CommandUnitType.EXEC);
    setName(CLEANUP_UNIT);
  }

  @Override
  public CommandExecutionStatus executeInternal(ShellCommandExecutionContext context) {
    return context.executeCommandString("rm -rf " + new File("/tmp", context.getActivityId()).getAbsolutePath());
  }
}
