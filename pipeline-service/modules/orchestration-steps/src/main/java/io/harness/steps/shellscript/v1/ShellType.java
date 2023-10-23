/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.shell.ScriptType;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.shellscript.v1.ShellType")
public enum ShellType {
  @JsonProperty(ShellTypeConstants.BASH) Bash(ScriptType.BASH),
  @JsonProperty(ShellTypeConstants.POWERSHELL) PowerShell(ScriptType.POWERSHELL);

  private final ScriptType scriptType;

  ShellType(ScriptType scriptType) {
    this.scriptType = scriptType;
  }

  public ScriptType getScriptType() {
    return scriptType;
  }
}
