/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.shell.SshSessionFactory.getSSHSession;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.delegate.task.winrm.WinRmSession;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.logging.NoopExecutionCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.SshClientManager;

import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.delegatetasks.validation.capabilities.ShellConnectionCapability;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.jcraft.jsch.JSchException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ShellConnectionCapabilityCheck implements CapabilityCheck {
  @Inject EncryptionService encryptionService;
  @Inject SecretManagementDelegateService secretManagementDelegateService;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    ShellConnectionCapability capability = (ShellConnectionCapability) delegateCapability;
    ShellScriptParameters parameters = capability.getShellScriptParameters();

    switch (parameters.getConnectionType()) {
      case SSH:
        return validateSshConnection(capability, parameters);
      case WINRM:
        return validateWinrmConnection(capability, parameters);
      default:
        log.error("This should Not happen");
        return CapabilityResponse.builder().validated(false).delegateCapability(capability).build();
    }
  }

  private CapabilityResponse validateWinrmConnection(
      ShellConnectionCapability capability, ShellScriptParameters parameters) {
    try {
      int timeout = (int) ofSeconds(15L).toMillis();
      WinRmSessionConfig winrmConfig = parameters.winrmSessionConfig(encryptionService);
      winrmConfig.setTimeout(timeout);
      log.info("Validating WinrmSession to Host: {}, Port: {}, useSsl: {}", winrmConfig.getHostname(),
          winrmConfig.getPort(), winrmConfig.isUseSSL());

      try (WinRmSession ignore = new WinRmSession(winrmConfig, new NoopExecutionCallback())) {
        return CapabilityResponse.builder().validated(true).delegateCapability(capability).build();
      }

    } catch (Exception ex) {
      log.info("Exception in sshSession Validation", ex);
      return CapabilityResponse.builder().validated(false).delegateCapability(capability).build();
    }
  }

  private CapabilityResponse validateSshConnection(
      ShellConnectionCapability capability, ShellScriptParameters parameters) {
    try {
      int timeout = (int) ofSeconds(15L).toMillis();
      SshSessionConfig expectedSshConfig =
          parameters.sshSessionConfig(encryptionService, secretManagementDelegateService);
      expectedSshConfig.setSocketConnectTimeout(timeout);
      expectedSshConfig.setSshConnectionTimeout(timeout);
      expectedSshConfig.setSshSessionTimeout(timeout);
      performTest(expectedSshConfig);
      return CapabilityResponse.builder().validated(true).delegateCapability(capability).build();
    } catch (JSchException ex) {
      log.info("Exception in sshSession Validation, cause {}", ex.getMessage());
      return CapabilityResponse.builder().validated(false).delegateCapability(capability).build();
    } catch (Exception ex) {
      log.info("Exception in sshSession Validation", ex);
      return CapabilityResponse.builder().validated(false).delegateCapability(capability).build();
    }
  }

  @VisibleForTesting
  void performTest(SshSessionConfig expectedSshConfig) throws Exception {
    if (expectedSshConfig.isUseSshClient() || expectedSshConfig.isVaultSSH()) {
      SshClientManager.test(expectedSshConfig);
    } else {
      getSSHSession(expectedSshConfig).disconnect();
    }
  }
}
