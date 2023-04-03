/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh;

import static io.harness.shell.AccessType.KEY_SUDO_APP_USER;
import static io.harness.shell.AccessType.KEY_SU_APP_USER;
import static io.harness.shell.ExecutorType.BASTION_HOST;
import static io.harness.shell.ExecutorType.KEY_AUTH;
import static io.harness.shell.ExecutorType.PASSWORD_AUTH;

import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.shell.AccessType;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.agent.SshClient;
import io.harness.shell.ssh.agent.jsch.JschClient;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class SshFactory {
  public static SshClient getSshClient(SshSessionConfig config) {
    return getSshClient(config, new NoopExecutionCallback());
  }

  public static SshClient getSshClient(SshSessionConfig config, LogCallback logCallback) {
    init(config);

    if (config.isVaultSSH()) {
      // this flow is planned to be migrated to SSHJ flows
      return new JschClient(config, logCallback);
    } else {
      return new JschClient(config, logCallback);
    }
  }

  private static void init(SshSessionConfig config) {
    if (config.getExecutorType() == null) {
      if (config.getBastionHostConfig() != null) {
        config.setExecutorType(BASTION_HOST);
      } else {
        if (config.getAccessType() == AccessType.KEY || config.getAccessType() == KEY_SU_APP_USER
            || config.getAccessType() == KEY_SUDO_APP_USER) {
          config.setExecutorType(KEY_AUTH);
        } else {
          config.setExecutorType(PASSWORD_AUTH);
        }
      }
    }
  }
}
