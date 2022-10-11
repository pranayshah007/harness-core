/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.winrm.WinRmCommandConstants.SESSION_TIMEOUT;
import static io.harness.delegate.task.utils.PhysicalDataCenterUtils.extractHostnameFromHost;
import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.CapabilityResponse.CapabilityResponseBuilder;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.WinrmConnectivityExecutionCapability;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.delegate.task.winrm.WinRmSession;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.NoopExecutionCallback;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.jcraft.jsch.JSchException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class WinrmHostConnectionCapabilityCheck implements CapabilityCheck {
  @Inject private SecretDecryptionService secretDecryptionService;
  private static final String WINDOWS_HOME_DIR = "%USERPROFILE%";

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    WinrmConnectivityExecutionCapability capability = (WinrmConnectivityExecutionCapability) delegateCapability;
    CapabilityResponseBuilder capabilityResponseBuilder = CapabilityResponse.builder().delegateCapability(capability);

    WinRmInfraDelegateConfig winRmInfraDelegateConfig = capability.getWinRmInfraDelegateConfig();

    if (winRmInfraDelegateConfig.getHosts().isEmpty()) {
      throw new InvalidRequestException("Hosts list cannot be empty");
    }

    WinRmSessionConfig config =
        createWinrmSessionConfig(winRmInfraDelegateConfig, capability.isUseWinRMKerberosUniqueCacheFile());
    log.info("Validating Winrm Session to Host: {}, Port: {}, useSsl: {}", config.getHostname(), config.getPort(),
        config.isUseSSL());

    try (WinRmSession ignore = connect(config)) {
      capabilityResponseBuilder.validated(true);
    } catch (Exception e) {
      log.info("Exception in WinrmSession Connection: ", ExceptionMessageSanitizer.sanitizeException(e));
      capabilityResponseBuilder.validated(false);
    }
    return capabilityResponseBuilder.build();
  }

  @VisibleForTesting
  WinRmSession connect(WinRmSessionConfig config) throws JSchException {
    return new WinRmSession(config, new NoopExecutionCallback());
  }

  private WinRmSessionConfig createWinrmSessionConfig(
      WinRmInfraDelegateConfig pdcWinrmInfraDelegateConfig, boolean useWinRMKerberosUniqueCacheFile) {
    WinRmCredentialsSpecDTO winRmCredentials = pdcWinrmInfraDelegateConfig.getWinRmCredentials();
    WinRmAuthDTO winRmAuthDTO = winRmCredentials.getAuth();
    int port = winRmCredentials.getPort();
    String firstHost = pdcWinrmInfraDelegateConfig.getHosts().iterator().next();
    String host = extractHostnameFromHost(firstHost).orElseThrow(
        ()
            -> new InvalidArgumentsException(
                format("Not found hostName, host: %s, extracted port: %s", firstHost, port), USER_SRE));
    switch (winRmAuthDTO.getAuthScheme()) {
      case NTLM:
        NTLMConfigDTO ntlmConfigDTO = (NTLMConfigDTO) winRmAuthDTO.getSpec();
        return generateWinRmSessionConfigForNTLM(
            host, ntlmConfigDTO, pdcWinrmInfraDelegateConfig.getEncryptionDataDetails(), port);
      case Kerberos:
        KerberosWinRmConfigDTO kerberosWinRmConfigDTO = (KerberosWinRmConfigDTO) winRmAuthDTO.getSpec();
        return generateWinRmSessionConfigForKerberos(host, kerberosWinRmConfigDTO,
            pdcWinrmInfraDelegateConfig.getEncryptionDataDetails(), port, useWinRMKerberosUniqueCacheFile);
      default:
        throw new IllegalArgumentException("Invalid authSchema provided:" + winRmAuthDTO.getAuthScheme());
    }
  }

  private WinRmSessionConfig generateWinRmSessionConfigForNTLM(
      String host, NTLMConfigDTO ntlmConfigDTO, List<EncryptedDataDetail> encryptionDetails, int port) {
    NTLMConfigDTO decryptedNTLMConfigDTO =
        (NTLMConfigDTO) secretDecryptionService.decrypt(ntlmConfigDTO, encryptionDetails);

    return WinRmSessionConfig.builder()
        .workingDirectory(WINDOWS_HOME_DIR)
        .hostname(host)
        .timeout(SESSION_TIMEOUT)
        .authenticationScheme(AuthenticationScheme.NTLM)
        .domain(ntlmConfigDTO.getDomain())
        .port(port)
        .username(ntlmConfigDTO.getUsername())
        .useSSL(ntlmConfigDTO.isUseSSL())
        .useNoProfile(ntlmConfigDTO.isUseNoProfile())
        .skipCertChecks(ntlmConfigDTO.isSkipCertChecks())
        .password(String.valueOf(decryptedNTLMConfigDTO.getPassword().getDecryptedValue()))
        .build();
  }

  private WinRmSessionConfig generateWinRmSessionConfigForKerberos(String host,
      KerberosWinRmConfigDTO kerberosWinRmConfigDTO, List<EncryptedDataDetail> encryptionDetails, int port,
      boolean useWinRMKerberosUniqueCacheFile) {
    boolean isUseKeyTab = false;
    String password = org.jooq.tools.StringUtils.EMPTY;
    String keyTabFilePath = org.jooq.tools.StringUtils.EMPTY;

    switch (kerberosWinRmConfigDTO.getTgtGenerationMethod()) {
      case Password:
        TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosWinRmConfigDTO.getSpec();
        TGTPasswordSpecDTO passwordSpecDTO =
            (TGTPasswordSpecDTO) secretDecryptionService.decrypt(tgtPasswordSpecDTO, encryptionDetails);

        password = String.valueOf(passwordSpecDTO.getPassword().getDecryptedValue());
        break;

      case KeyTabFilePath:
        TGTKeyTabFilePathSpecDTO tgtKeyTabFilePathSpecDTO = (TGTKeyTabFilePathSpecDTO) kerberosWinRmConfigDTO.getSpec();
        isUseKeyTab = true;
        keyTabFilePath = tgtKeyTabFilePathSpecDTO.getKeyPath();
        break;

      default:
        throw new IllegalArgumentException(
            "Invalid TgtGenerationMethod provided:" + kerberosWinRmConfigDTO.getTgtGenerationMethod());
    }

    return WinRmSessionConfig.builder()
        .workingDirectory(WINDOWS_HOME_DIR)
        .hostname(host)
        .timeout(SESSION_TIMEOUT)
        .authenticationScheme(AuthenticationScheme.KERBEROS)
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
        .build();
  }
}
