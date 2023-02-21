/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.sam;

import org.apache.commons.lang3.StringUtils;

public class DeployCommand extends AwsSamAbstractExecutable {
  private AwsSamClient client;
  private String region;
  private String stackName;
  private String options;

  public DeployCommand(AwsSamClient client) {
    this.client = client;
  }


  public DeployCommand region(String region) {
    this.region = region;
    return this;
  }

  public DeployCommand stackName(String stackName) {
    this.stackName = stackName;
    return this;
  }

  public DeployCommand options(String options) {
    this.options = options;
    return this;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder(2048);
    command.append(client.command()).append("deploy ");

    if (StringUtils.isNotBlank(this.stackName)) {
      command.append(AwsSamClient.option(Option.stackName, this.stackName));
    }

    if (StringUtils.isNotBlank(this.region)) {
      command.append(AwsSamClient.option(Option.region, this.region));
    }

    if (StringUtils.isNotBlank(this.options)) {
      command.append(this.options);
    }
    return command.toString().trim();
  }
}
