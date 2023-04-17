/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gcpcli;

import io.harness.k8s.kubectl.AbstractExecutable;

import org.apache.commons.lang3.StringUtils;

public class AuthCommand extends AbstractExecutable {
  private GcpCliClient client;
  private String keyFile;

  public AuthCommand(GcpCliClient client) {
    this.client = client;
  }

  public AuthCommand keyFile(String keyFile) {
    this.keyFile = keyFile;
    return this;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder();
    command.append(client.command()).append("auth activate-service-account ");

    if (StringUtils.isNotBlank(this.keyFile)) {
      command.append(GcpCliClient.option(Option.KEY_FILE, this.keyFile));
    }

    command.append(GcpCliClient.option(Option.QUIET));

    return command.toString().trim();
  }
}
