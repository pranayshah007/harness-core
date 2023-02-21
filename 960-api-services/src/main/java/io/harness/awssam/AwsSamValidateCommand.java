/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.awssam;

import org.apache.commons.lang3.StringUtils;

public class AwsSamValidateCommand extends AbstractExecutable {
  private AwsSamClient client;
  private String templatePath;
  private String region;
  private String configPath;
  private String options;

  public AwsSamValidateCommand(AwsSamClient client) {
    this.client = client;
  }

  public AwsSamValidateCommand templatePath(String templatePath) {
    this.templatePath = templatePath;
    return this;
  }

  public AwsSamValidateCommand region(String region) {
    this.region = region;
    return this;
  }

  public AwsSamValidateCommand configPath(String configPath) {
    this.configPath = configPath;
    return this;
  }

  public AwsSamValidateCommand options(String options) {
    this.options = options;
    return this;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder(2048);
    command.append(client.command()).append("validate ");
    if (StringUtils.isNotBlank(this.templatePath)) {
      command.append(AwsSamClient.option(SamOption.templatePath, this.templatePath));
    }
    if (StringUtils.isNotBlank(this.region)) {
      command.append(AwsSamClient.option(SamOption.region, this.region));
    }
    if (StringUtils.isNotBlank(this.configPath)) {
      command.append(AwsSamClient.option(SamOption.configPath, this.configPath));
    }
    if (StringUtils.isNotBlank(this.options)) {
      command.append(this.options);
    }
    return command.toString().trim();
  }
}
