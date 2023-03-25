package io.harness.shell.ssh.agent.jsch;

import io.harness.shell.ssh.agent.SshSession;

import com.jcraft.jsch.ChannelSftp;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class JschSftpSession extends SshSession {
  private ChannelSftp channel;
  @Override
  public void close() throws Exception {
    channel.disconnect();
  }
}
