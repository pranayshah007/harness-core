/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.az;

import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;

import org.apache.commons.lang3.StringUtils;

public class Az {
  private final String azPath;

  private Az(String azPath) {
    this.azPath = azPath;
  }

  public static Az client(String azPath) {
    return new Az(azPath);
  }

  public VersionCommand version() {
    return new VersionCommand(this);
  }

  public AuthCommand auth() {
    return new AuthCommand(this);
  }

  public String command() {
    StringBuilder command = new StringBuilder(128);
    if (StringUtils.isNotBlank(azPath)) {
      command.append(encloseWithQuotesIfNeeded(azPath)).append(' ');
    } else {
      command.append("az ");
    }
    return command.toString();
  }

  public static String option(Option type, String value) {
    return type.toString() + " " + value + " ";
  }

  public static String flag(AuthType type) {
    return "--" + type.toString() + " ";
  }
}
