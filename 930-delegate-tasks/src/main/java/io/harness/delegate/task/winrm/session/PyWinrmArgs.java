/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.winrm.session;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.SPACE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;

import java.util.Collections;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@OwnedBy(CDP)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class PyWinrmArgs {
  private String username;
  private String hostname;
  private Map<String, String> environmentMap;
  private String workingDir;
  private Integer timeout;
  private boolean serverCertValidation;

  public String getArgs(String commandFilePath) {
    return format("-e '%s' -u '%s' -s '%s' -env %s -w '%s' -t '%s' -cfile '%s'", hostname, username,
        serverCertValidation, buildEnvironmentStrForCommandLine(), workingDir, timeout, commandFilePath);
  }

  private String buildEnvironmentStrForCommandLine() {
    if (environmentMap == null || EmptyPredicate.isEmpty(environmentMap)) {
      return String.format("%s", Collections.emptyMap());
    }
    return environmentMap.entrySet()
        .stream()
        .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
        .map(this::quoteAndEscapeQuote)
        .collect(joining(SPACE));
  }

  /**
   * <ul>
   *   <li>Replaces the single quotes inside the key or value to escaped single quotes and checks if the value ends
   *   with slash, add one more slash so that the single quote at the end won't get esccaped</li>
   *   <li>The regex uses lookbehind to check how many slashes are present before the single quote.
   *   It only replaces if there are even number of slashes which would be considered as non-escaped inside shell.
   *   </li>
   * </ul>
   *
   */
  private String quoteAndEscapeQuote(String val) {
    // Checks if there are even number of slashes before quote. If yes, then add one extra slash to make it odd so
    // that the quote becomes escaped single quote. For example ' become \' and \\' becomes \\\'
    String formattedValue = val.replaceAll("(?<!\\\\)(?:\\\\{2})*'", "\\\\'");
    if (formattedValue.endsWith("\\") && !formattedValue.endsWith("\\\\")) {
      formattedValue += "\\";
    }
    return String.format("$'%s'", formattedValue);
  }
}
