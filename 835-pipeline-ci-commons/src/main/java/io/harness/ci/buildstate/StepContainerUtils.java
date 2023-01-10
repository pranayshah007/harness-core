/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.buildstate;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.UNIX_STEP_COMMAND;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.WIN_STEP_COMMAND;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CI)
public class StepContainerUtils {
  public static List<String> getCommand(OSType os) {
    String cmd = UNIX_STEP_COMMAND;
    if (os == OSType.Windows) {
      cmd = WIN_STEP_COMMAND;
    }

    List<String> command = new ArrayList<>();
    command.add(cmd);
    return command;
  }

  public static List<String> getArguments(Integer port) {
    List<String> args = new ArrayList<>();
    args.add(PORT_PREFIX);
    args.add(port.toString());
    return args;
  }
}
