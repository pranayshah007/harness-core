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
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.product.ci.scm.proto.SCMGrpc;
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
  @Inject private ScmConnectorMapperDelegate scmConnectorMapperDelegate;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    GitConnectionNGCapability capability = (GitConnectionNGCapability) delegateCapability;
    try {
      if (capability.isOptimizedFilesFetch()) {
        checkCapabilityForScm(capability.getGitConfig(), capability.getEncryptedDataDetails());
      } else {
        checkCapabilityForJgit(capability);
      }
    } catch (Exception e) {
      return CapabilityResponse.builder().delegateCapability(capability).validated(false).build();
    }
    return CapabilityResponse.builder().delegateCapability(capability).validated(true).build();
  }

  private void checkCapabilityForJgit(GitConnectionNGCapability capability) {
    GitConfigDTO gitConfig =
        scmConnectorMapperDelegate.toGitConfigDTO(capability.getGitConfig(), capability.getEncryptedDataDetails());
    List<EncryptedDataDetail> encryptedDataDetails = capability.getEncryptedDataDetails();
    SshSessionConfig sshSessionConfig = null;

    try {
      if (gitConfig.getGitAuthType() == GitAuthType.HTTP) {
        decryptionService.decrypt(gitConfig.getGitAuth(), encryptedDataDetails);
      } else if (gitConfig.getGitAuthType() == GitAuthType.SSH) {
        sshSessionConfig =
            getSSHConfigIfSSHCredsAreUsed(capability.getSshKeySpecDTO(), capability.getEncryptedDataDetails());
      }
    } catch (Exception e) {
      log.info("Failed to decrypt " + capability.getGitConfig(), e);
      throw e;
    }
    gitService.validateOrThrow(gitConfig, delegateConfiguration.getAccountId(), sshSessionConfig);
  }

  private void checkCapabilityForScm(ScmConnector scmConnector, List<EncryptedDataDetail> encryptedDataDetails) {
    try {
      gitDecryptionHelper.decryptApiAccessConfig(scmConnector, encryptedDataDetails);
    } catch (Exception e) {
      log.info("Failed to decrypt " + scmConnector, e);
      throw e;
    }
    scmDelegateClient.processScmRequest(c -> scmServiceClient.listBranches(scmConnector, SCMGrpc.newBlockingStub(c)));
  }

  private SshSessionConfig getSSHConfigIfSSHCredsAreUsed(
      SSHKeySpecDTO sshKeySpecDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    return sshSessionConfigMapper.getSSHSessionConfig(sshKeySpecDTO, encryptedDataDetails);
  }
}
