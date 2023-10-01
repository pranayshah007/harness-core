/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.windows;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@UtilityClass
@Slf4j
public class CmdUtils {
  public static final String WIN_RM_MARKER = "WinRm";

  public static String escapeEnvValueSpecialChars(String value) {
    /*
    https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/set_1
    */

    if (!isValidEnvValue(value)) {
      log.warn(format("Escaping %s env variable as there is a single %% occurrence", value));
      return "";
    }

    value = value.replace("^", "^^");
    value = value.replace("<", "^<");
    value = value.replace(">", "^>");
    value = value.replace("|", "^|");
    value = value.replace("&", "^&");
    value = value.replace("%", "^%");

    return value;
  }

  public static String escapeEnvValueIllegalSymbols(String value) {
    if (!isValidEnvValue(value)) {
      log.debug(format("Escaping %s env variable as there is a single %% occurrence", value));
      return "";
    }

    /*
    < and > symbols can be used standalone, but in combination of those is translating to %lt> string itself.
     */
    if (value.contains("<>")) {
      log.debug(format("Escaping %s env variable as there is a a combination of <> symbols identified", value));
      return "";
    }

    return value;
  }

  public static String escapeLineBreakChars(String value) {
    return value.replace("\r", "`r").replace("\n", "`n").replace("\t", "`t");
  }

  public static String escapeWordBreakChars(String value) {
    return value.replace(" ", "` ");
  }

  private static boolean isValidEnvValue(String value) {
    /*
    Rules:
    Should contain even number of '%' chars
    pair of '%' cannot be adjacent - unless they are part of different pairs
    path       - Valid
    %path      - Invalid
    %path%     - Valid
    %pa%th%    - InValid
    %pa%%th%   - Valid
     */

    boolean valid = true;
    int currentIndex = -1;
    int occurrences = 0;
    while (true) {
      int nextIndex = value.indexOf('%', currentIndex + 1);
      if (nextIndex == -1) {
        break;
      }
      occurrences++;
      if (nextIndex == currentIndex + 1) {
        if (occurrences % 2 == 0) {
          valid = false;
          break;
        }
      }
      currentIndex = nextIndex;
    }
    if (occurrences % 2 != 0) {
      valid = false;
    }
    return valid;
  }
}
