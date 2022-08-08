/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serverless;

import org.apache.commons.lang3.StringUtils;

public class ConfigCredentialCommand extends AbstractExecutable {
  private ServerlessClient client;
  private String provider;
  private String key;
  private String secret;
  private boolean overwrite;
  public ConfigCredentialCommand(ServerlessClient client) {
    this.client = client;
  }
  public ConfigCredentialCommand provider(String provider) {
    this.provider = provider;
    return this;
  }
  public ConfigCredentialCommand key(String key) {
    this.key = key;
    return this;
  }
  public ConfigCredentialCommand secret(String secret) {
    this.secret = secret;
    return this;
  }
  public ConfigCredentialCommand overwrite(boolean overwrite) {
    this.overwrite = overwrite;
    return this;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder(2048);
    command.append(client.command()).append("config credentials ");
    if (StringUtils.isNotBlank(this.provider)) {
      command.append(ServerlessClient.option(Option.provider, this.provider));
    }
    if (StringUtils.isNotBlank(this.key)) {
      command.append(ServerlessClient.option(Option.key, this.key));
    }
    if (StringUtils.isNotBlank(this.secret)) {
      command.append(ServerlessClient.option(Option.secret, this.secret));
    }
    if (this.overwrite) {
      command.append(ServerlessClient.flag(Flag.overwrite));
    }
    return command.toString().trim();
  }
}
