/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh;

import io.harness.logging.LogCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.connection.ExecCommandData;
import io.harness.shell.ssh.connection.ExecResponse;
import io.harness.shell.ssh.xfer.ScpCommandData;
import io.harness.shell.ssh.xfer.ScpResponse;

public class JschClient extends SshClient {
  @Override
  ExecResponse exec(ExecCommandData commandData) {
    return null;
  }

  @Override
  ScpResponse scp(ScpCommandData commandData) {
    return null;
  }

  @Override
  HSshClient getSession(SshSessionConfig sshSessionConfig, LogCallback logCallback) {
    return null;
  }

  @Override
  void close() {}

  @Override
  void configureProxy() {}
}
