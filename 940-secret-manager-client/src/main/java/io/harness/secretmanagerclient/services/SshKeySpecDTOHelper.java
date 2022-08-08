/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.services;

import static java.util.Collections.emptyList;

import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.KerberosBaseConfigDTO;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class SshKeySpecDTOHelper {
  @Inject private SecretManagerClientService secretManagerClientService;

  public List<EncryptedDataDetail> getSSHKeyEncryptionDetails(SSHKeySpecDTO secretSpecDTO, NGAccess ngAccess) {
    switch (secretSpecDTO.getAuth().getAuthScheme()) {
      case SSH:
        SSHConfigDTO sshConfigDTO = (SSHConfigDTO) secretSpecDTO.getAuth().getSpec();
        return getSSHEncryptionDetails(sshConfigDTO, ngAccess);
      case Kerberos:
        KerberosConfigDTO kerberosConfigDTO = (KerberosConfigDTO) secretSpecDTO.getAuth().getSpec();
        return getKerberosEncryptionDetails(kerberosConfigDTO, ngAccess);
      default:
        return emptyList();
    }
  }

  List<EncryptedDataDetail> getSSHEncryptionDetails(SSHConfigDTO sshConfigDTO, NGAccess ngAccess) {
    switch (sshConfigDTO.getCredentialType()) {
      case Password:
        SSHPasswordCredentialDTO sshPasswordCredentialDTO = (SSHPasswordCredentialDTO) sshConfigDTO.getSpec();
        return secretManagerClientService.getEncryptionDetails(ngAccess, sshPasswordCredentialDTO);
      case KeyReference:
        SSHKeyReferenceCredentialDTO sshKeyReferenceCredentialDTO =
            (SSHKeyReferenceCredentialDTO) sshConfigDTO.getSpec();
        return secretManagerClientService.getEncryptionDetails(ngAccess, sshKeyReferenceCredentialDTO);
      case KeyPath:
        SSHKeyPathCredentialDTO sshKeyPathCredentialDTO = (SSHKeyPathCredentialDTO) sshConfigDTO.getSpec();
        return secretManagerClientService.getEncryptionDetails(ngAccess, sshKeyPathCredentialDTO);
      default:
        return emptyList();
    }
  }

  public List<EncryptedDataDetail> getKerberosEncryptionDetails(
      KerberosBaseConfigDTO kerberosConfigDTO, NGAccess ngAccess) {
    switch (kerberosConfigDTO.getTgtGenerationMethod()) {
      case Password:
        TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosConfigDTO.getSpec();
        return secretManagerClientService.getEncryptionDetails(ngAccess, tgtPasswordSpecDTO);
      case KeyTabFilePath:
        TGTKeyTabFilePathSpecDTO tgtKeyTabFilePathSpecDTO = (TGTKeyTabFilePathSpecDTO) kerberosConfigDTO.getSpec();
        return secretManagerClientService.getEncryptionDetails(ngAccess, tgtKeyTabFilePathSpecDTO);
      default:
        return emptyList();
    }
  }
}
