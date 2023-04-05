/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.secrets.tasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.SSHTaskParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.secrets.SSHConfigValidationTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshSessionFactory;

import com.google.inject.Inject;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(PL)
@Slf4j
public class SSHConfigValidationDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private SecretDecryptionService secretDecryptionService;

  public SSHConfigValidationDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  private SshSessionConfig getSSHSessionConfig(
      SSHKeySpecDTO sshKeySpecDTO, List<EncryptedDataDetail> encryptionDetails) {
    SshSessionConfig.Builder builder = aSshSessionConfig().withPort(sshKeySpecDTO.getPort());
    SSHAuthDTO authDTO = sshKeySpecDTO.getAuth();
    switch (authDTO.getAuthScheme()) {
      case SSH:
        SSHConfigDTO sshConfigDTO = (SSHConfigDTO) authDTO.getSpec();
        SshSessionConfigHelper.generateSSHBuilder(
            authDTO, sshConfigDTO, builder, encryptionDetails, secretDecryptionService);
        break;
      case Kerberos:
        KerberosConfigDTO kerberosConfigDTO = (KerberosConfigDTO) authDTO.getSpec();
        SshSessionConfigHelper.generateKerberosBuilder(
            authDTO, kerberosConfigDTO, builder, encryptionDetails, secretDecryptionService);
        break;
      default:
        break;
    }
    builder.withSshConnectionTimeout(30000);
    return builder.build();
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    SSHTaskParams sshTaskParams = (SSHTaskParams) parameters;

    SshSessionConfig sshSessionConfig =
        getSSHSessionConfig(sshTaskParams.getSshKeySpec(), sshTaskParams.getEncryptionDetails());
    sshSessionConfig.setHost(sshTaskParams.getHost());
    sshSessionConfig.setPort(sshTaskParams.getSshKeySpec().getPort());
    try {
      Session session = SshSessionFactory.getSSHSession(sshSessionConfig);
      session.disconnect();
      return SSHConfigValidationTaskResponse.builder().connectionSuccessful(true).build();
    } catch (JSchException e) {
      return SSHConfigValidationTaskResponse.builder().connectionSuccessful(false).errorMessage(e.getMessage()).build();
    }
  }
}
