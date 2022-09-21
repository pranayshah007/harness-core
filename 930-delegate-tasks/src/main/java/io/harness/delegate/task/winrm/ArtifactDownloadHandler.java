package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.shell.ScriptType;

@OwnedBy(CDP)
public interface ArtifactDownloadHandler {
  String getCommandString(
      SshWinRmArtifactDelegateConfig artifactDelegateConfig, String destinationPath, ScriptType scriptType);
}
