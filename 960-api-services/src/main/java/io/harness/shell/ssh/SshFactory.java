/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh;

import io.harness.logging.LogCallback;
import io.harness.shell.SshSessionConfig;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class SshFactory {
  public SshClient getSshClient(SshClientType sshClientType, SshSessionConfig config, LogCallback logCallback) {
    SshClient client;

    if (null == sshClientType) {
      client = new JschClient();
    } else {
      switch (sshClientType) {
        case JSCH:
          client = new JschClient();
          break;
        default:
          throw new NotImplementedException("Ssh client type not implemented: " + sshClientType);
      }
    }

    client.init(config, logCallback);

    return client;
  }
  public enum SshClientType { JSCH, SSHJ }
}
