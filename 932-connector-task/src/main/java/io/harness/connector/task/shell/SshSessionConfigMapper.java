/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.shell.AuthenticationScheme.KERBEROS;
import static io.harness.shell.AuthenticationScheme.SSH_KEY;
import static io.harness.shell.KerberosConfig.KerberosConfigBuilder;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;
import static io.harness.utils.SecretUtils.validateDecryptedValue;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.helper.DecryptionHelper;
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
import io.harness.shell.AccessType;
import io.harness.shell.KerberosConfig;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@OwnedBy(CDP)
@Singleton
public class SshSessionConfigMapper {
  @Inject DecryptionHelper decryptionHelper;

  public SshSessionConfig getSSHSessionConfig(
      SSHKeySpecDTO sshKeySpecDTO, List<EncryptedDataDetail> encryptionDetails) {
    SshSessionConfig.Builder builder = aSshSessionConfig().withPort(sshKeySpecDTO.getPort());
    SSHAuthDTO authDTO = sshKeySpecDTO.getAuth();
    switch (authDTO.getAuthScheme()) {
      case SSH:
        SSHConfigDTO sshConfigDTO = (SSHConfigDTO) authDTO.getSpec();
        generateSSHBuilder(authDTO, sshConfigDTO, builder, encryptionDetails);
        break;
      case Kerberos:
        KerberosConfigDTO kerberosConfigDTO = (KerberosConfigDTO) authDTO.getSpec();
        generateKerberosBuilder(authDTO, kerberosConfigDTO, builder, encryptionDetails);
        break;
      default:
        break;
    }
    return builder.build();
  }

  private void generateSSHBuilder(SSHAuthDTO authDTO, SSHConfigDTO sshConfigDTO, SshSessionConfig.Builder builder,
      List<EncryptedDataDetail> encryptionDetails) {
    switch (sshConfigDTO.getCredentialType()) {
      case Password:
        SSHPasswordCredentialDTO sshPasswordCredentialDTO = (SSHPasswordCredentialDTO) sshConfigDTO.getSpec();
        SSHPasswordCredentialDTO passwordCredentialDTO =
            (SSHPasswordCredentialDTO) decryptionHelper.decrypt(sshPasswordCredentialDTO, encryptionDetails);
        char[] decryptedValue = passwordCredentialDTO.getPassword().getDecryptedValue();
        validateDecryptedValue(decryptedValue, passwordCredentialDTO.getPassword().getIdentifier());
        builder.withAccessType(AccessType.USER_PASSWORD)
            .withUserName(passwordCredentialDTO.getUserName())
            .withSshPassword(decryptedValue);
        break;
      case KeyReference:
        SSHKeyReferenceCredentialDTO sshKeyReferenceCredentialDTO =
            (SSHKeyReferenceCredentialDTO) sshConfigDTO.getSpec();
        // since files are base 64 encoded, we decode it before using it
        SSHKeyReferenceCredentialDTO keyReferenceCredentialDTO =
            (SSHKeyReferenceCredentialDTO) decryptionHelper.decrypt(sshKeyReferenceCredentialDTO, encryptionDetails);
        char[] fileData = keyReferenceCredentialDTO.getKey().getDecryptedValue();
        validateDecryptedValue(fileData, keyReferenceCredentialDTO.getKey().getIdentifier());
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
            (SSHKeyPathCredentialDTO) decryptionHelper.decrypt(sshKeyPathCredentialDTO, encryptionDetails);
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
    builder.withUseSshj(authDTO.isUseSshj());
    builder.withUseSshClient(authDTO.isUseSshClient());
  }

  private void generateKerberosBuilder(SSHAuthDTO authDTO, KerberosConfigDTO kerberosConfigDTO,
      SshSessionConfig.Builder builder, List<EncryptedDataDetail> encryptionDetails) {
    KerberosConfigBuilder kerberosConfigBuilder = KerberosConfig.builder()
                                                      .principal(kerberosConfigDTO.getPrincipal())
                                                      .realm(kerberosConfigDTO.getRealm())
                                                      .generateTGT(kerberosConfigDTO.getTgtGenerationMethod() != null);

    if (kerberosConfigDTO.getTgtGenerationMethod() != null) { // skip no TGT
      switch (kerberosConfigDTO.getTgtGenerationMethod()) {
        case Password:
          TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosConfigDTO.getSpec();
          TGTPasswordSpecDTO passwordSpecDTO =
              (TGTPasswordSpecDTO) decryptionHelper.decrypt(tgtPasswordSpecDTO, encryptionDetails);
          char[] decryptedValue = passwordSpecDTO.getPassword().getDecryptedValue();
          validateDecryptedValue(decryptedValue, passwordSpecDTO.getPassword().getIdentifier());
          builder.withPassword(decryptedValue);
          break;
        case KeyTabFilePath:
          TGTKeyTabFilePathSpecDTO tgtKeyTabFilePathSpecDTO = (TGTKeyTabFilePathSpecDTO) kerberosConfigDTO.getSpec();
          TGTKeyTabFilePathSpecDTO keyTabFilePathSpecDTO =
              (TGTKeyTabFilePathSpecDTO) decryptionHelper.decrypt(tgtKeyTabFilePathSpecDTO, encryptionDetails);
          kerberosConfigBuilder.keyTabFilePath(keyTabFilePathSpecDTO.getKeyPath());
          break;
        default:
          break;
      }
    }
    builder.withAuthenticationScheme(KERBEROS)
        .withAccessType(AccessType.KERBEROS)
        .withKerberosConfig(kerberosConfigBuilder.build())
        .withUseSshClient(authDTO.isUseSshClient())
        .withUseSshj(authDTO.isUseSshj());
  }
}
