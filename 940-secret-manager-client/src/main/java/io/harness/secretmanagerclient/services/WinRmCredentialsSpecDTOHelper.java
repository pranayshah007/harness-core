/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.services;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.KerberosBaseConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@OwnedBy(CDP)
@Singleton
public class WinRmCredentialsSpecDTOHelper {
  @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private SshKeySpecDTOHelper sshKeySpecDTOHelper;

  public List<EncryptedDataDetail> getWinRmEncryptionDetails(WinRmCredentialsSpecDTO secretSpecDTO, NGAccess ngAccess) {
    switch (secretSpecDTO.getAuth().getAuthScheme()) {
      case NTLM:
        NTLMConfigDTO ntlmConfigDTO = (NTLMConfigDTO) secretSpecDTO.getAuth().getSpec();
        return secretManagerClientService.getEncryptionDetails(ngAccess, ntlmConfigDTO);
      case Kerberos:
        KerberosBaseConfigDTO kerberosConfigDTO = (KerberosBaseConfigDTO) secretSpecDTO.getAuth().getSpec();
        return sshKeySpecDTOHelper.getKerberosEncryptionDetails(kerberosConfigDTO, ngAccess);
      default:
        return emptyList();
    }
  }
}
