/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.client.sshj;

import io.harness.shell.ssh.client.SshConnection;
import io.harness.shell.ssh.exception.SshClientException;
import io.harness.shell.ssh.exception.SshjClientException;

import java.io.IOException;
import lombok.Builder;
import lombok.Getter;
import net.schmizz.sshj.SSHClient;

@Builder
@Getter
public class SshjConnection extends SshConnection {
  private SSHClient client;
  @Override
  public void close() throws SshClientException {
    try {
      client.close();
    } catch (IOException e) {
      throw new SshjClientException(e.getMessage());
    }
  }
}
