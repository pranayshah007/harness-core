/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.sam;

import org.apache.commons.lang3.StringUtils;

public class AwsSamPackageCommand extends AbstractExecutable {
  private AwsSamClient client;
  private String templatePath;
  private String region;
  private String configPath;
  private String outputTemplateFilePath;
  private String options;

  public AwsSamPackageCommand(AwsSamClient client) {
    this.client = client;
  }

  public AwsSamPackageCommand templatePath(String templatePath) {
    this.templatePath = templatePath;
    return this;
  }

  public AwsSamPackageCommand region(String region) {
    this.region = region;
    return this;
  }

  public AwsSamPackageCommand configPath(String configPath) {
    this.configPath = configPath;
    return this;
  }

  public AwsSamPackageCommand outputTemplateFilePath(String templateFilePath) {
    this.outputTemplateFilePath = templateFilePath;
    return this;
  }

  public AwsSamPackageCommand options(String options) {
    this.options = options;
    return this;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder(2048);
    command.append(client.command()).append("package ");
    if (StringUtils.isNotBlank(this.templatePath)) {
      command.append(AwsSamClient.option(SamOption.templatePath, this.templatePath));
    }
    if (StringUtils.isNotBlank(this.region)) {
      command.append(AwsSamClient.option(SamOption.region, this.region));
    }
    if (StringUtils.isNotBlank(this.configPath)) {
      command.append(AwsSamClient.option(SamOption.configPath, this.configPath));
    }
    if (StringUtils.isNotBlank(this.outputTemplateFilePath)) {
      command.append(AwsSamClient.option(SamOption.outputTemplateFilePath, this.outputTemplateFilePath));
    }
    if (StringUtils.isNotBlank(this.options)) {
      command.append(this.options);
    }
    return command.toString().trim();
  }
}
