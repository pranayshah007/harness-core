/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils.artifacts;

import static io.harness.shell.ScriptType.POWERSHELL;

import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.DownloadArtifactCommandUnit.Builder.aDownloadArtifactCommandUnit;
import static software.wings.beans.command.ExecCommandUnit.Builder.anExecCommandUnit;
import static software.wings.utils.PowerShellScriptsLoader.psScriptMap;

import static java.util.Arrays.asList;

import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.CommandUnit;
import software.wings.utils.PowerShellScriptsLoader;

import java.util.List;

public class IISAppArtifactCommands implements ArtifactCommands {
  @Override
  public boolean isInternal() {
    return false;
  }

  @Override
  public List<Command> getDefaultCommands() {
    return asList(getInstallCommand());
  }

  private Command getInstallCommand() {
    return aCommand()
        .withCommandType(CommandType.INSTALL)
        .withName("Install IIS Application")
        .withCommandUnits(
            asList(new CommandUnit[] {aDownloadArtifactCommandUnit()
                                          .withName(PowerShellScriptsLoader.PsScript.DownloadArtifact.getDisplayName())
                                          .withScriptType(POWERSHELL)
                                          .withCommandPath("$env:TEMP")
                                          .build(),
                anExecCommandUnit()
                    .withName(PowerShellScriptsLoader.PsScript.ExpandArtifacts.getDisplayName())
                    .withScriptType(POWERSHELL)
                    .withCommandString(psScriptMap.get(PowerShellScriptsLoader.PsScript.ExpandArtifacts))
                    .build(),
                anExecCommandUnit()
                    .withName(PowerShellScriptsLoader.PsScript.CreateIISVirtualDirectory.getDisplayName())
                    .withScriptType(POWERSHELL)
                    .withCommandString(psScriptMap.get(PowerShellScriptsLoader.PsScript.CreateIISVirtualDirectory))
                    .build()}))
        .build();
  }
}
