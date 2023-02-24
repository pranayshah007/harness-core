/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.sam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
public class AwsSamClient {
  public AwsSamValidateCommand validate() {
    return new AwsSamValidateCommand(this);
  }

  public AwsSamBuildCommand build() {
    return new AwsSamBuildCommand(this);
  }

  public AwsSamPackageCommand packagee() {
    return new AwsSamPackageCommand(this);
  }

  public AwsSamPublishCommand publish() {
    return new AwsSamPublishCommand(this);
  }

  public String command() {
    StringBuilder command = new StringBuilder(256);
    command.append("sam ");
    return command.toString();
  }

  public static String option(SamOption type, String value) {
    return "--" + type.toString() + " " + value + " ";
  }
}
