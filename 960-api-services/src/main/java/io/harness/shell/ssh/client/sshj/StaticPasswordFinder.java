package io.harness.shell.ssh.client.sshj;

import net.schmizz.sshj.userauth.password.PasswordFinder;

public class StaticPasswordFinder implements PasswordFinder {
  private final char[] password;
  public StaticPasswordFinder(String password) {
    this.password = password.toCharArray();
  }
  public char[] reqPassword(net.schmizz.sshj.userauth.password.Resource<?> resource) {
    return password;
  }
  public boolean shouldRetry(net.schmizz.sshj.userauth.password.Resource<?> resource) {
    return false;
  }
}