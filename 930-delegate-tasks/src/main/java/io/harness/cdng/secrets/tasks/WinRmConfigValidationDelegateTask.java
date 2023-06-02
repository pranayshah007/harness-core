/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.secrets.tasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.utils.SecretUtils.validateDecryptedValue;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.WinRmTaskParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.secrets.WinRmConfigValidationTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.delegate.task.winrm.WinRmSession;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.delegate.task.winrm.WinRmSessionConfig.WinRmSessionConfigBuilder;
import io.harness.logging.NoopExecutionCallback;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@Slf4j
public class WinRmConfigValidationDelegateTask extends AbstractDelegateRunnableTask {
  private static final String HOME_DIR = "%USERPROFILE%";

  @Inject private SecretDecryptionService secretDecryptionService;

  public WinRmConfigValidationDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  private WinRmSessionConfig getWinRmSessionConfig(WinRmTaskParams params) {
    WinRmCredentialsSpecDTO winRmCredentialsSpecDTO = params.getSpec();
    WinRmAuthDTO authDTO = winRmCredentialsSpecDTO.getAuth();
    List<EncryptedDataDetail> encryptionDetails = params.getEncryptionDetails();

    WinRmSessionConfigBuilder builder =
        WinRmSessionConfig.builder()
            .workingDirectory(HOME_DIR)
            .hostname(params.getHost())
            .port(winRmCredentialsSpecDTO.getPort())
            .environment(Collections.EMPTY_MAP)
            .commandParameters(params.getSpec() != null ? params.getSpec().getParameters() : Collections.emptyList())
            .timeout(1800000);

    switch (authDTO.getAuthScheme()) {
      case NTLM:
        NTLMConfigDTO ntlmConfigDTO = (NTLMConfigDTO) authDTO.getSpec();
        return generateWinRmSessionConfigForNTLM(ntlmConfigDTO, builder, encryptionDetails);
      case Kerberos:
        KerberosWinRmConfigDTO kerberosWinRmConfigDTO = (KerberosWinRmConfigDTO) authDTO.getSpec();
        return generateWinRmSessionConfigForKerberos(kerberosWinRmConfigDTO, builder, encryptionDetails);
      default:
        throw new IllegalArgumentException("Invalid authSchema provided:" + authDTO.getAuthScheme());
    }
  }

  private WinRmSessionConfig generateWinRmSessionConfigForNTLM(
      NTLMConfigDTO ntlmConfigDTO, WinRmSessionConfigBuilder builder, List<EncryptedDataDetail> encryptionDetails) {
    NTLMConfigDTO decryptedNTLMConfigDTO =
        (NTLMConfigDTO) secretDecryptionService.decrypt(ntlmConfigDTO, encryptionDetails);

    char[] decryptedValue = decryptedNTLMConfigDTO.getPassword().getDecryptedValue();
    validateDecryptedValue(decryptedValue, decryptedNTLMConfigDTO.getPassword().getIdentifier());

    builder.authenticationScheme(AuthenticationScheme.NTLM)
        .domain(ntlmConfigDTO.getDomain())
        .username(ntlmConfigDTO.getUsername())
        .useSSL(ntlmConfigDTO.isUseSSL())
        .useNoProfile(ntlmConfigDTO.isUseNoProfile())
        .skipCertChecks(ntlmConfigDTO.isSkipCertChecks())
        .password(String.valueOf(decryptedValue));

    return builder.build();
  }

  private WinRmSessionConfig generateWinRmSessionConfigForKerberos(KerberosWinRmConfigDTO kerberosWinRmConfigDTO,
      WinRmSessionConfigBuilder builder, List<EncryptedDataDetail> encryptionDetails) {
    boolean isUseKeyTab = false;
    String password = StringUtils.EMPTY;
    String keyTabFilePath = StringUtils.EMPTY;

    if (kerberosWinRmConfigDTO.getTgtGenerationMethod() != null) { // skip No TGT
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
        .username(kerberosWinRmConfigDTO.getPrincipal())
        .useSSL(kerberosWinRmConfigDTO.isUseSSL())
        .useNoProfile(kerberosWinRmConfigDTO.isUseNoProfile())
        .skipCertChecks(kerberosWinRmConfigDTO.isSkipCertChecks())
        .useKeyTab(isUseKeyTab)
        .keyTabFilePath(keyTabFilePath)
        .password(password);

    return builder.build();
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    WinRmSessionConfig winRmSessionConfig = getWinRmSessionConfig((WinRmTaskParams) parameters);
    try (WinRmSession ignore = new WinRmSession(winRmSessionConfig, new NoopExecutionCallback())) {
      return WinRmConfigValidationTaskResponse.builder().connectionSuccessful(true).build();
    } catch (Exception e) {
      log.info("Exception in WinRmSession Validation", e);
      return WinRmConfigValidationTaskResponse.builder()
          .connectionSuccessful(false)
          .errorMessage(e.getMessage())
          .build();
    }
  }
}
