package io.harness.cdng.gitops;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.shell.ScriptType;

@OwnedBy(HarnessTeam.GITOPS)
public enum ShellType {
  Bash(ScriptType.BASH);

  private final ScriptType scriptType;

  ShellType(ScriptType scriptType) {
    this.scriptType = scriptType;
  }

  public ScriptType getScriptType() {
    return scriptType;
  }
}
