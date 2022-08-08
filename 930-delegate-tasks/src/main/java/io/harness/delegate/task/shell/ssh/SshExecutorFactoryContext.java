/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@OwnedBy(HarnessTeam.CDP)
public class SshExecutorFactoryContext {
  private final String accountId;
  private final String executionId;
  private final String commandUnitName;
  private final String workingDirectory;
  private final Map<String, String> environment;
  private final CommandUnitsProgress commandUnitsProgress;
  private final ILogStreamingTaskClient iLogStreamingTaskClient;
  private final SSHKeySpecDTO sshKeySpecDTO;
  private final List<EncryptedDataDetail> encryptedDataDetailList;
  private final boolean executeOnDelegate;
  private final String host;
  // artifact
  private final String destinationPath;
  private final SshWinRmArtifactDelegateConfig artifactDelegateConfig;
  private final Map<String, String> artifactMetadata = new HashMap<>();
}
