/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogHelper.doneColoring;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.LogLevel;

import software.wings.beans.LogHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(PIPELINE)
public class LogStreamingHelper {
  public static String IS_SIMPLIFY = "isSimplified";

  public void colorLog(LogLine logLine) {
    String message = logLine.getMessage();
    if (logLine.getLevel() == LogLevel.ERROR) {
      message = color(message, Red, Bold);
    } else if (logLine.getLevel() == LogLevel.WARN) {
      message = color(message, Yellow, Bold);
    }
    message = doneColoring(message);
    logLine.setMessage(message);
  }

  @Nonnull
  public String generateLogBaseKey(LinkedHashMap<String, String> logStreamingAbstractions) {
    if (logStreamingAbstractions.containsKey(IS_SIMPLIFY) && logStreamingAbstractions.get(IS_SIMPLIFY).equals("true")) {
      return generateSimplifiedLogBaseKey(logStreamingAbstractions);
    }

    // Generate base log key that will be used for writing logs to log streaming service
    StringBuilder logBaseKey = new StringBuilder();
    for (Map.Entry<String, String> entry : logStreamingAbstractions.entrySet()) {
      if (logBaseKey.length() != 0) {
        logBaseKey.append('/');
      }

      logBaseKey.append(entry.getKey()).append(':').append(entry.getValue());
    }

    return logBaseKey.toString();
  }

  @Nonnull
  public String generateSimplifiedLogBaseKey(LinkedHashMap<String, String> logStreamingAbstractions) {
    if (logStreamingAbstractions.containsKey(IS_SIMPLIFY) && logStreamingAbstractions.get(IS_SIMPLIFY).equals("true")) {
      logStreamingAbstractions.remove(IS_SIMPLIFY, "true");
    }

    // Generate base log key that will be used for writing logs to log streaming service
    StringBuilder logBaseKey = new StringBuilder();
    for (Map.Entry<String, String> entry : logStreamingAbstractions.entrySet()) {
      if (logBaseKey.length() != 0) {
        logBaseKey.append('/');
      }

      logBaseKey.append(entry.getValue());
    }

    return logBaseKey.toString();
  }

  public String generateLogKeyGivenCommandUnit(String baseLogKey, String commandUnit) {
    return baseLogKey + String.format(LogHelper.COMMAND_UNIT_PLACEHOLDER, commandUnit);
  }
}
