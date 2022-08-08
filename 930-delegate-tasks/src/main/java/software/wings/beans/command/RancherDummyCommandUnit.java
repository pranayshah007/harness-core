/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static software.wings.beans.command.CommandUnitType.RANCHER_DUMMY;

import io.harness.logging.CommandExecutionStatus;

import org.apache.commons.lang3.NotImplementedException;

public class RancherDummyCommandUnit extends AbstractCommandUnit {
  public RancherDummyCommandUnit(String name) {
    super(RANCHER_DUMMY);
    setName(name);
  }

  public RancherDummyCommandUnit() {
    super(RANCHER_DUMMY);
  }

  @Override
  public CommandExecutionStatus execute(CommandExecutionContext context) {
    throw new NotImplementedException("Not implemented");
  }
}
