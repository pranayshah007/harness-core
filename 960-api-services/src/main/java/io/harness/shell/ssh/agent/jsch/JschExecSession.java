package io.harness.shell.ssh.agent.jsch;

import io.harness.shell.ssh.agent.SshSession;

import com.jcraft.jsch.ChannelExec;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class JschExecSession extends SshSession {
  private ChannelExec channel;
  @Override
  public void close() throws Exception {
    channel.disconnect();
  }
}
