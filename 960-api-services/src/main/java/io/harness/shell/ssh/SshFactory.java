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
import io.harness.shell.AccessType;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.agent.SshAgent;
import io.harness.shell.ssh.agent.jsch.JschAgent;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class SshFactory {
  public SshAgent getSshClient(SshClientType sshClientType, SshSessionConfig config, LogCallback logCallback) {
    SshAgent client;

    if (null == sshClientType) {
      client = new JschAgent();
    } else {
      switch (sshClientType) {
        case JSCH:
          client = new JschAgent();
          break;
        default:
          throw new NotImplementedException("Ssh client type not implemented: " + sshClientType);
      }
    }

    client.init(config, logCallback);
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

    return client;
  }
  public enum SshClientType { JSCH, SSHJ }
}
