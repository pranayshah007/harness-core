/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azurecli;

import io.harness.k8s.kubectl.AbstractExecutable;

import org.apache.commons.lang3.StringUtils;

public class AuthCommand extends AbstractExecutable {
  private AzureCliClient client;
  private String clientId;
  private String tenantId;
  private String certPath;
  private AuthType authType;

  public AuthCommand(AzureCliClient client) {
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

  public AuthCommand certPath(String certPath) {
    this.certPath = certPath;
    return this;
  }

  public AuthCommand authType(AuthType authType) {
    this.authType = authType;
    return this;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder();
    command.append(client.command()).append("login ").append(AzureCliClient.flag(authType));

    if (StringUtils.isNotBlank(this.clientId)) {
      command.append(AzureCliClient.option(Option.CLIENT_ID, this.clientId));
    }

    if (this.certPath != null) {
      command.append(AzureCliClient.option(Option.CERT_PATH, this.certPath));
    }

    if (StringUtils.isNotBlank(this.tenantId)) {
      command.append(AzureCliClient.option(Option.TENANT_ID, this.tenantId));
    }

    return command.toString().trim();
  }
}
