/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DX)
public class GitConnectionNGCapabilityChecker implements CapabilityCheck {
  @Inject private SecretDecryptionService decryptionService;
  @Inject private NGGitService gitService;
  @Inject private DelegateConfiguration delegateConfiguration;
  @Inject private SshSessionConfigMapper sshSessionConfigMapper;
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private ScmDelegateClient scmDelegateClient;
  @Inject private ScmServiceClient scmServiceClient;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    GitConnectionNGCapability capability = (GitConnectionNGCapability) delegateCapability;
    GitConfigDTO gitConfig = capability.getGitConfig();
    List<EncryptedDataDetail> encryptedDataDetails = capability.getEncryptedDataDetails();
    SshSessionConfig sshSessionConfig = null;
    GithubConnectorDTO githubConnectorDTO = capability.getGithubConnectorDTO();

    try {
      if (gitConfig.getGitAuthType() == GitAuthType.HTTP) {
        if (capability.isGithubAppAuthentication()) {
          gitDecryptionHelper.decryptApiAccessConfig(githubConnectorDTO, capability.getEncryptedDataDetails());
        } else {
          decryptionService.decrypt(gitConfig.getGitAuth(), encryptedDataDetails);
        }
      } else if (gitConfig.getGitAuthType() == GitAuthType.SSH) {
        sshSessionConfig =
            getSSHConfigIfSSHCredsAreUsed(capability.getSshKeySpecDTO(), capability.getEncryptedDataDetails());
      }
    } catch (Exception e) {
      log.info("Failed to decrypt " + capability.getGitConfig(), e);
      return CapabilityResponse.builder().delegateCapability(capability).validated(false).build();
    }
    String accountId = delegateConfiguration.getAccountId();
    try {
      if (capability.isGithubAppAuthentication()) {
        scmDelegateClient.processScmRequest(
            c -> scmServiceClient.listBranches(githubConnectorDTO, SCMGrpc.newBlockingStub(c)));

      } else {
        gitService.validateOrThrow(gitConfig, accountId, sshSessionConfig);
      }
    } catch (Exception e) {
      return CapabilityResponse.builder().delegateCapability(capability).validated(false).build();
    }
    return CapabilityResponse.builder().delegateCapability(capability).validated(true).build();
  }

  private SshSessionConfig getSSHConfigIfSSHCredsAreUsed(
      SSHKeySpecDTO sshKeySpecDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    return sshSessionConfigMapper.getSSHSessionConfig(sshKeySpecDTO, encryptedDataDetails);
  }
}
