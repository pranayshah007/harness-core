/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.secrets.tasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.shell.AuthenticationScheme.KERBEROS;
import static io.harness.shell.AuthenticationScheme.SSH_KEY;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.SSHTaskParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.secrets.SSHConfigValidationTaskResponse;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.AccessType;
import io.harness.shell.KerberosConfig;
import io.harness.shell.KerberosConfig.KerberosConfigBuilder;
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
        generateSSHBuilder(sshConfigDTO, builder, encryptionDetails);
        break;
      case Kerberos:
        KerberosConfigDTO kerberosConfigDTO = (KerberosConfigDTO) authDTO.getSpec();
        generateKerberosBuilder(kerberosConfigDTO, builder, encryptionDetails);
        break;
      default:
        break;
    }
    builder.withSshConnectionTimeout(30000);
    return builder.build();
  }

  private void generateSSHBuilder(
      SSHConfigDTO sshConfigDTO, SshSessionConfig.Builder builder, List<EncryptedDataDetail> encryptionDetails) {
    switch (sshConfigDTO.getCredentialType()) {
      case Password:
        SSHPasswordCredentialDTO sshPasswordCredentialDTO = (SSHPasswordCredentialDTO) sshConfigDTO.getSpec();
        SSHPasswordCredentialDTO passwordCredentialDTO =
            (SSHPasswordCredentialDTO) secretDecryptionService.decrypt(sshPasswordCredentialDTO, encryptionDetails);
        builder.withAccessType(AccessType.USER_PASSWORD)
            .withUserName(passwordCredentialDTO.getUserName())
            .withSshPassword(passwordCredentialDTO.getPassword().getDecryptedValue());
        break;
      case KeyReference:
        SSHKeyReferenceCredentialDTO sshKeyReferenceCredentialDTO =
            (SSHKeyReferenceCredentialDTO) sshConfigDTO.getSpec();
        // since files are base 64 encoded, we decode it before using it
        SSHKeyReferenceCredentialDTO keyReferenceCredentialDTO =
            (SSHKeyReferenceCredentialDTO) secretDecryptionService.decrypt(
                sshKeyReferenceCredentialDTO, encryptionDetails);
        char[] fileData = keyReferenceCredentialDTO.getKey().getDecryptedValue();
        keyReferenceCredentialDTO.getKey().setDecryptedValue(new String(fileData).toCharArray());
        builder.withAccessType(AccessType.KEY)
            .withKeyName("Key")
            .withKey(keyReferenceCredentialDTO.getKey().getDecryptedValue())
            .withUserName(keyReferenceCredentialDTO.getUserName());
        if (null != keyReferenceCredentialDTO.getEncryptedPassphrase()) {
          builder.withKeyPassphrase(keyReferenceCredentialDTO.getEncryptedPassphrase().getDecryptedValue());
        }
        break;
      case KeyPath:
        SSHKeyPathCredentialDTO sshKeyPathCredentialDTO = (SSHKeyPathCredentialDTO) sshConfigDTO.getSpec();
        SSHKeyPathCredentialDTO keyPathCredentialDTO =
            (SSHKeyPathCredentialDTO) secretDecryptionService.decrypt(sshKeyPathCredentialDTO, encryptionDetails);
        builder.withKeyPath(keyPathCredentialDTO.getKeyPath())
            .withUserName(keyPathCredentialDTO.getUserName())
            .withAccessType(AccessType.KEY)
            .withKeyLess(true)
            .build();
        break;
      default:
        break;
    }
    builder.withAuthenticationScheme(SSH_KEY);
  }

  private void generateKerberosBuilder(KerberosConfigDTO kerberosConfigDTO, SshSessionConfig.Builder builder,
      List<EncryptedDataDetail> encryptionDetails) {
    KerberosConfigBuilder kerberosConfig = KerberosConfig.builder()
                                               .principal(kerberosConfigDTO.getPrincipal())
                                               .realm(kerberosConfigDTO.getRealm())
                                               .generateTGT(kerberosConfigDTO.getTgtGenerationMethod() != null);
    if (kerberosConfigDTO.getTgtGenerationMethod() != null) { // skip no TGT
      switch (kerberosConfigDTO.getTgtGenerationMethod()) {
        case Password:
          TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosConfigDTO.getSpec();
          TGTPasswordSpecDTO passwordSpecDTO =
              (TGTPasswordSpecDTO) secretDecryptionService.decrypt(tgtPasswordSpecDTO, encryptionDetails);
          builder.withPassword(passwordSpecDTO.getPassword().getDecryptedValue());
          break;
        case KeyTabFilePath:
          TGTKeyTabFilePathSpecDTO tgtKeyTabFilePathSpecDTO = (TGTKeyTabFilePathSpecDTO) kerberosConfigDTO.getSpec();
          TGTKeyTabFilePathSpecDTO keyTabFilePathSpecDTO =
              (TGTKeyTabFilePathSpecDTO) secretDecryptionService.decrypt(tgtKeyTabFilePathSpecDTO, encryptionDetails);
          kerberosConfig.keyTabFilePath(keyTabFilePathSpecDTO.getKeyPath());
          break;
        default:
          break;
      }
    }
    builder.withAuthenticationScheme(KERBEROS)
        .withAccessType(AccessType.KERBEROS)
        .withKerberosConfig(kerberosConfig.build());
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
    try {
      Session session = SshSessionFactory.getSSHSession(sshSessionConfig);
      session.disconnect();
      return SSHConfigValidationTaskResponse.builder().connectionSuccessful(true).build();
    } catch (JSchException e) {
      return SSHConfigValidationTaskResponse.builder().connectionSuccessful(false).errorMessage(e.getMessage()).build();
    }
  }
}
