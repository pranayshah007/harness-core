/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.shell.ScriptType;

@OwnedBy(PIPELINE)
public enum ShellType {
  Bash(ScriptType.BASH),
  PowerShell(ScriptType.POWERSHELL);

  private final ScriptType scriptType;

  ShellType(ScriptType scriptType) {
    this.scriptType = scriptType;
  }

  public ScriptType getScriptType() {
    return scriptType;
  }
}
