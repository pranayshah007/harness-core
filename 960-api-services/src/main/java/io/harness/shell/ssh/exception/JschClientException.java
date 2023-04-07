/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.exception;

import io.harness.eraro.ErrorCode;

public class JschClientException extends SshClientException {
  private static String client = "JSCH";

  public JschClientException(String message) {
    super(client, message);
  }

  public JschClientException(ErrorCode errorCode, Throwable cause) {
    super(client, errorCode, cause);
  }

  public JschClientException(String message, Throwable cause) {
    super(client, message, cause);
  }

  public JschClientException(ErrorCode errorCode, String message, Throwable cause) {
    super(client, message, errorCode, cause);
  }

  public JschClientException(ErrorCode errorCode) {
    super(client, errorCode);
  }
}
