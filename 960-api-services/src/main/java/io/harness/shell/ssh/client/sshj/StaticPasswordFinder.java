/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

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