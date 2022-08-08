/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serverless;

import org.apache.commons.lang3.StringUtils;

public class PluginCommand extends AbstractExecutable {
  private ServerlessClient client;
  private String pluginName;
  private String config;
  public PluginCommand(ServerlessClient client) {
    this.client = client;
  }
  public PluginCommand pluginName(String pluginName) {
    this.pluginName = pluginName;
    return this;
  }
  public PluginCommand config(String config) {
    this.config = config;
    return this;
  }
  @Override
  public String command() {
    StringBuilder command = new StringBuilder(2048);
    command.append(client.command()).append("plugin install ");
    if (StringUtils.isNotBlank(this.pluginName)) {
      command.append(ServerlessClient.option(Option.name, this.pluginName));
    }
    if (StringUtils.isNotBlank(this.config)) {
      command.append(ServerlessClient.option(Option.config, this.config));
    }
    return command.toString().trim();
  }
}
