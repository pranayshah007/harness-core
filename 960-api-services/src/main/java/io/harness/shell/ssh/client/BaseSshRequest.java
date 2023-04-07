package io.harness.shell.ssh.client;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BaseSshRequest {
  private boolean retry = false;
  private SshClientType clientType = SshClientType.JSCH;
  public enum SshClientType { JSCH, SSHJ }
}
