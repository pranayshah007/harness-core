/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.stream.LogOutputStream;

@EqualsAndHashCode(callSuper = false)
@Slf4j
@OwnedBy(CDP)
public class PlanHumanReadableOutputStream extends LogOutputStream {
  private String humanReadablePlan = "";
  private static String NEW_LINE = "\n";
  @Override
  protected void processLine(String s) {
    // Only append when not empty, Add a new line after the string so it looks better as a plan in the end
    if (!s.isEmpty()) {
      humanReadablePlan += s + NEW_LINE;
    }
  }
  public String getHumanReadablePlan() {
    return humanReadablePlan;
  }
}
