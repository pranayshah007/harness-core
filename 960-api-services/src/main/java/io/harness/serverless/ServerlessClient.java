/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serverless;

import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class ServerlessClient {
  private String serverlessPath;

  private ServerlessClient(String serverlessPath) {
    this.serverlessPath = serverlessPath;
  }

  public static ServerlessClient client(String serverlessPath) {
    return new ServerlessClient(serverlessPath);
  }

  public VersionCommand version() {
    return new VersionCommand(this);
  }

  public DeployCommand deploy() {
    return new DeployCommand(this);
  }

  public ConfigCredentialCommand configCredential() {
    return new ConfigCredentialCommand(this);
  }

  public PluginCommand plugin() {
    return new PluginCommand(this);
  }

  public DeployListCommand deployList() {
    return new DeployListCommand(this);
  }

  public RollbackCommand rollback() {
    return new RollbackCommand(this);
  }

  public RemoveCommand remove() {
    return new RemoveCommand(this);
  }

  public String command() {
    StringBuilder command = new StringBuilder(256);
    if (StringUtils.isNotBlank(serverlessPath)) {
      command.append(ServerlessUtils.encloseWithQuotesIfNeeded(serverlessPath));
    } else {
      command.append("serverless ");
    }
    return command.toString();
  }

  public static String option(Option type, String value) {
    return "--" + type.toString() + " " + value + " ";
  }

  public static String flag(Flag type) {
    return "--" + type.toString() + " ";
  }

  public static String home(String directory) {
    return "HOME=" + directory + " ";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ServerlessClient that = (ServerlessClient) o;
    return Objects.equals(serverlessPath, that.serverlessPath);
  }
}
