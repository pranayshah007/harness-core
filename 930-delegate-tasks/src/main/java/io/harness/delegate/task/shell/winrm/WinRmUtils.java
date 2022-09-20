package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.winrm.WinRmCommandConstants.SESSION_TIMEOUT;

import static software.wings.common.Constants.WINDOWS_HOME_DIR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.delegate.task.winrm.WinRmSessionConfig.WinRmSessionConfigBuilder;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class WinRmUtils {
  public static WinRmSessionConfig getWinRmSessionConfig(NgCommandUnit commandUnit,
      WinrmTaskParameters winRmCommandTaskParameters, WinRmConfigAuthEnhancer winRmConfigAuthEnhancer) {
    WinRmSessionConfigBuilder configBuilder = WinRmSessionConfig.builder()
                                                  .accountId(winRmCommandTaskParameters.getAccountId())
                                                  .executionId(winRmCommandTaskParameters.getExecutionId())
                                                  .workingDirectory(WINDOWS_HOME_DIR)
                                                  .commandUnitName(commandUnit.getName())
                                                  .environment(winRmCommandTaskParameters.getEnvironmentVariables())
                                                  .hostname(winRmCommandTaskParameters.getHost())
                                                  .timeout(SESSION_TIMEOUT);

    final WinRmInfraDelegateConfig winRmInfraDelegateConfig = winRmCommandTaskParameters.getWinRmInfraDelegateConfig();
    if (winRmInfraDelegateConfig == null) {
      throw new InvalidRequestException("Task parameters must include WinRm Infra Delegate config.");
    }

    return winRmConfigAuthEnhancer.configureAuthentication(winRmInfraDelegateConfig.getWinRmCredentials(),
        winRmInfraDelegateConfig.getEncryptionDataDetails(), configBuilder,
        winRmCommandTaskParameters.isUseWinRMKerberosUniqueCacheFile());
  }
}
