/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.secrets.tasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.shell.AuthenticationScheme.KERBEROS;
import static io.harness.shell.AuthenticationScheme.SSH_KEY;
import static io.harness.shell.KerberosConfig.KerberosConfigBuilder;
import static io.harness.utils.SecretUtils.validateDecryptedValue;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.AccessType;
import io.harness.shell.KerberosConfig;
import io.harness.shell.SshSessionConfig;

import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class SshSessionConfigHelper {
  public void generateKerberosBuilder(SSHAuthDTO authDTO, KerberosConfigDTO kerberosConfigDTO,
      SshSessionConfig.Builder builder, List<EncryptedDataDetail> encryptionDetails,
      SecretDecryptionService secretDecryptionService) {
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
          char[] decryptedValue = passwordSpecDTO.getPassword().getDecryptedValue();
          validateDecryptedValue(decryptedValue, passwordSpecDTO.getPassword().getIdentifier());
          builder.withPassword(decryptedValue);
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
        .withKerberosConfig(kerberosConfig.build())
        .withUseSshj(authDTO.isUseSshj())
        .withUseSshClient(authDTO.isUseSshClient());
  }

  public void generateSSHBuilder(SSHAuthDTO authDTO, SSHConfigDTO sshConfigDTO, SshSessionConfig.Builder builder,
      List<EncryptedDataDetail> encryptionDetails, SecretDecryptionService secretDecryptionService) {
    switch (sshConfigDTO.getCredentialType()) {
      case Password:
        SSHPasswordCredentialDTO sshPasswordCredentialDTO = (SSHPasswordCredentialDTO) sshConfigDTO.getSpec();
        SSHPasswordCredentialDTO passwordCredentialDTO =
            (SSHPasswordCredentialDTO) secretDecryptionService.decrypt(sshPasswordCredentialDTO, encryptionDetails);
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
            (SSHKeyReferenceCredentialDTO) secretDecryptionService.decrypt(
                sshKeyReferenceCredentialDTO, encryptionDetails);
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
    builder.withUseSshClient(authDTO.isUseSshClient());
    builder.withUseSshj(authDTO.isUseSshj());
  }
}
