/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.CapabilityResponse.CapabilityResponseBuilder;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.delegate.task.winrm.WinRmSession;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.WinRmConnectionAttributes;
import software.wings.delegatetasks.validation.capabilities.BasicValidationInfo;
import software.wings.delegatetasks.validation.capabilities.WinrmHostValidationCapability;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.jcraft.jsch.JSchException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class WinrmHostValidationCapabilityCheck implements CapabilityCheck {
  @Inject private EncryptionService encryptionService;
  private static final String WINDOWS_HOME_DIR = "%USERPROFILE%";

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    WinrmHostValidationCapability capability = (WinrmHostValidationCapability) delegateCapability;
    CapabilityResponseBuilder capabilityResponseBuilder = CapabilityResponse.builder().delegateCapability(capability);
    WinRmConnectionAttributes connectionAttributes = capability.getWinRmConnectionAttributes();
    List<EncryptedDataDetail> encryptedDataDetails = capability.getWinrmConnectionEncryptedDataDetails();
    encryptionService.decrypt(connectionAttributes, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(connectionAttributes, encryptedDataDetails);

    WinRmSessionConfig config =
        winrmSessionConfig(capability.getValidationInfo(), connectionAttributes, capability.getEnvVariables());
    log.info("Validating Winrm Session to Host: {}, Port: {}, useSsl: {}", config.getHostname(), config.getPort(),
        config.isUseSSL());

    try (WinRmSession ignore = makeSession(config, new NoopExecutionCallback())) {
      capabilityResponseBuilder.validated(true);
    } catch (Exception e) {
      log.info("Exception in WinrmSession Validation: {}", ExceptionMessageSanitizer.sanitizeException(e));
      capabilityResponseBuilder.validated(false);
    }
    return capabilityResponseBuilder.build();
  }

  private WinRmSessionConfig winrmSessionConfig(BasicValidationInfo validationInfo,
      WinRmConnectionAttributes winrmConnectionAttributes, Map<String, String> envVariables) {
    return WinRmSessionConfig.builder()
        .accountId(validationInfo.getAccountId())
        .appId(validationInfo.getAppId())
        .executionId(validationInfo.getActivityId())
        .commandUnitName("HOST_CONNECTION_TEST")
        .hostname(validationInfo.getPublicDns())
        .authenticationScheme(winrmConnectionAttributes.getAuthenticationScheme())
        .domain(winrmConnectionAttributes.getDomain())
        .username(winrmConnectionAttributes.getUsername())
        .password(winrmConnectionAttributes.isUseKeyTab() ? StringUtils.EMPTY
                                                          : String.valueOf(winrmConnectionAttributes.getPassword()))
        .port(winrmConnectionAttributes.getPort())
        .useSSL(winrmConnectionAttributes.isUseSSL())
        .skipCertChecks(winrmConnectionAttributes.isSkipCertChecks())
        .useKeyTab(winrmConnectionAttributes.isUseKeyTab())
        .keyTabFilePath(winrmConnectionAttributes.getKeyTabFilePath())
        .workingDirectory(WINDOWS_HOME_DIR)
        .environment(envVariables == null ? Collections.emptyMap() : envVariables)
        .useNoProfile(winrmConnectionAttributes.isUseNoProfile())
        .build();
  }

  @VisibleForTesting
  WinRmSession makeSession(WinRmSessionConfig config, LogCallback logCallback) throws JSchException {
    return new WinRmSession(config, logCallback);
  }
}
