/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.utils.SecretUtils.validateDecryptedValue;

import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.delegate.task.winrm.WinRmSessionConfig.WinRmSessionConfigBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCommandParameter;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.util.List;
import org.jooq.tools.StringUtils;

public class WinRmConfigAuthEnhancer {
  private final SecretDecryptionService secretDecryptionService;

  @Inject
  public WinRmConfigAuthEnhancer(SecretDecryptionService secretDecryptionService) {
    this.secretDecryptionService = secretDecryptionService;
  }

  public WinRmSessionConfig configureAuthentication(WinRmCredentialsSpecDTO winRmCredentialsSpecDTO,
      List<EncryptedDataDetail> encryptionDetails, WinRmSessionConfigBuilder builder,
      boolean useWinRMKerberosUniqueCacheFile) {
    if (winRmCredentialsSpecDTO == null) {
      throw new InvalidRequestException("WinRm credentials are not specified.");
    }

    WinRmAuthDTO winRmAuthDTO = winRmCredentialsSpecDTO.getAuth();
    int port = winRmCredentialsSpecDTO.getPort();
    switch (winRmAuthDTO.getAuthScheme()) {
      case NTLM:
        NTLMConfigDTO ntlmConfigDTO = (NTLMConfigDTO) winRmAuthDTO.getSpec();
        return generateWinRmSessionConfigForNTLM(
            ntlmConfigDTO, builder, encryptionDetails, port, winRmCredentialsSpecDTO.getParameters());
      case Kerberos:
        KerberosWinRmConfigDTO kerberosWinRmConfigDTO = (KerberosWinRmConfigDTO) winRmAuthDTO.getSpec();
        return generateWinRmSessionConfigForKerberos(kerberosWinRmConfigDTO, builder, encryptionDetails, port,
            useWinRMKerberosUniqueCacheFile, winRmCredentialsSpecDTO.getParameters());
      default:
        throw new IllegalArgumentException("Invalid authSchema provided:" + winRmAuthDTO.getAuthScheme());
    }
  }

  private WinRmSessionConfig generateWinRmSessionConfigForNTLM(NTLMConfigDTO ntlmConfigDTO,
      WinRmSessionConfigBuilder builder, List<EncryptedDataDetail> encryptionDetails, int port,
      List<WinRmCommandParameter> commandParameters) {
    NTLMConfigDTO decryptedNTLMConfigDTO =
        (NTLMConfigDTO) secretDecryptionService.decrypt(ntlmConfigDTO, encryptionDetails);

    char[] decryptedValue = decryptedNTLMConfigDTO.getPassword().getDecryptedValue();
    validateDecryptedValue(decryptedValue, decryptedNTLMConfigDTO.getPassword().getIdentifier());
    builder.authenticationScheme(AuthenticationScheme.NTLM)
        .domain(ntlmConfigDTO.getDomain())
        .port(port)
        .username(ntlmConfigDTO.getUsername())
        .useSSL(ntlmConfigDTO.isUseSSL())
        .useNoProfile(ntlmConfigDTO.isUseNoProfile())
        .skipCertChecks(ntlmConfigDTO.isSkipCertChecks())
        .password(String.valueOf(decryptedValue))
        .commandParameters(commandParameters);

    return builder.build();
  }

  private WinRmSessionConfig generateWinRmSessionConfigForKerberos(KerberosWinRmConfigDTO kerberosWinRmConfigDTO,
      WinRmSessionConfigBuilder builder, List<EncryptedDataDetail> encryptionDetails, int port,
      boolean useWinRMKerberosUniqueCacheFile, List<WinRmCommandParameter> commandParameters) {
    boolean isUseKeyTab = false;
    String password = StringUtils.EMPTY;
    String keyTabFilePath = StringUtils.EMPTY;

    if (kerberosWinRmConfigDTO.getTgtGenerationMethod() != null) { // skip no TGT
      switch (kerberosWinRmConfigDTO.getTgtGenerationMethod()) {
        case Password:
          TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosWinRmConfigDTO.getSpec();
          TGTPasswordSpecDTO passwordSpecDTO =
              (TGTPasswordSpecDTO) secretDecryptionService.decrypt(tgtPasswordSpecDTO, encryptionDetails);

          char[] decryptedValue = passwordSpecDTO.getPassword().getDecryptedValue();
          validateDecryptedValue(decryptedValue, passwordSpecDTO.getPassword().getIdentifier());
          password = String.valueOf(decryptedValue);
          break;

        case KeyTabFilePath:
          TGTKeyTabFilePathSpecDTO tgtKeyTabFilePathSpecDTO =
              (TGTKeyTabFilePathSpecDTO) kerberosWinRmConfigDTO.getSpec();
          isUseKeyTab = true;
          keyTabFilePath = tgtKeyTabFilePathSpecDTO.getKeyPath();
          break;

        default:
          throw new IllegalArgumentException(
              "Invalid TgtGenerationMethod provided:" + kerberosWinRmConfigDTO.getTgtGenerationMethod());
      }
    }
    builder.authenticationScheme(AuthenticationScheme.KERBEROS)
        .domain(kerberosWinRmConfigDTO.getRealm())
        .port(port)
        .username(kerberosWinRmConfigDTO.getPrincipal())
        .useSSL(kerberosWinRmConfigDTO.isUseSSL())
        .useNoProfile(kerberosWinRmConfigDTO.isUseNoProfile())
        .skipCertChecks(kerberosWinRmConfigDTO.isSkipCertChecks())
        .useKeyTab(isUseKeyTab)
        .keyTabFilePath(keyTabFilePath)
        .password(password)
        .useKerberosUniqueCacheFile(useWinRMKerberosUniqueCacheFile)
        .commandParameters(commandParameters);

    return builder.build();
  }
}
