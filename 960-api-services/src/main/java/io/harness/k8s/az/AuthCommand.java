/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.az;

import io.harness.k8s.kubectl.AbstractExecutable;

import org.apache.commons.lang3.StringUtils;

public class AuthCommand extends AbstractExecutable {
  private Az client;
  private String clientId;
  private String tenantId;
  private char[] password;
  private byte[] cert;
  private String username;
  private AuthType authType;

  public AuthCommand(Az client) {
    this.client = client;
  }

  public AuthCommand clientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  public AuthCommand tenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public AuthCommand password(char[] password) {
    this.password = password;
    return this;
  }

  public AuthCommand cert(byte[] cert) {
    this.cert = cert;
    return this;
  }

  public AuthCommand username(String username) {
    this.username = username;
    return this;
  }

  public AuthCommand authType(AuthType authType) {
    this.authType = authType;
    return this;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder();
    command.append(client.command()).append("login ").append(Az.flag(authType));

    if (StringUtils.isNotBlank(this.clientId)) {
      command.append(Az.option(Option.clientId, this.clientId));
    }

    if (this.password != null) {
      command.append(Az.option(Option.password, String.valueOf(this.password)));
    }

    if (this.cert != null) {
      command.append(Az.option(Option.cert, new String(this.cert)));
    }

    if (StringUtils.isNotBlank(this.tenantId)) {
      command.append(Az.option(Option.tenantId, this.tenantId));
    }

    if (StringUtils.isNotBlank(this.username)) {
      command.append(Az.option(Option.username, this.username));
    }

    return command.toString().trim();
  }
}
