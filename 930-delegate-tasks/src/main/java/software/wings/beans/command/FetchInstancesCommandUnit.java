/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import io.harness.logging.CommandExecutionStatus;

import org.apache.commons.lang3.NotImplementedException;

public class FetchInstancesCommandUnit extends AbstractCommandUnit {
  /**
   * Required for CommandUnitDescriptor.newInstance
   */
  public FetchInstancesCommandUnit() {
    super(CommandUnitType.FETCH_INSTANCES_DUMMY);
  }

  /**
   * Instantiates a new Command unit.
   */
  public FetchInstancesCommandUnit(String name) {
    this();
    setName(name);
  }

  /**
   * Execute execution result.
   *
   * @param context the context
   * @return the execution result
   */
  @Override
  public CommandExecutionStatus execute(CommandExecutionContext context) {
    throw new NotImplementedException("Not implemented");
  }
}
