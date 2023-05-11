/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh.exception;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;

public class SshClientException extends WingsException {
  private static String generateMessage(String client) {
    return generateMessage(client, "");
  }
  private static String generateMessage(String client, String message) {
    return "[Client: " + client + "]" + (isEmpty(message) ? "" : " - " + message);
  }
  public SshClientException(String client, String message, Throwable cause) {
    super(generateMessage(client, message), cause);
  }

  public SshClientException(String client, String message) {
    super(generateMessage(client, message));
  }

  public SshClientException(String client, ErrorCode errorCode) {
    super(errorCode, generateMessage(client));
  }

  public SshClientException(String client, ErrorCode errorCode, Throwable throwable) {
    super(errorCode, generateMessage(client), throwable);
  }

  public SshClientException(String client, String message, ErrorCode errorCode, Throwable throwable) {
    super(errorCode, generateMessage(client, message), throwable);
  }
}
