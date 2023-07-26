/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.shell.ScriptType;

import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
// Tested with bash script and powershell currently is not supported
public class NGShellScriptFunctor implements ExpressionFunctor {
  private ScriptType scriptType;

  public NGShellScriptFunctor(ScriptType scriptType) {
    this.scriptType = scriptType;
  }

  public String escape(String input) {
    if (ScriptType.BASH == scriptType) {
      return input.replace("'", "\\'")
          .replace("`", "\\`")
          .replace("$", "\\$")
          .replace("&", "\\&")
          .replace("(", "\\(")
          .replace(")", "\\)")
          .replace("|", "\\|")
          .replace(";", "\\;")
          .replace("\"", "\\\"");
    } else if (ScriptType.POWERSHELL == scriptType) {
      return "\"" + input.replace("\"", "`\"") + "\"";
    }
    return input;
  }
  public String quote(String input) {
    return "\'" + input + "\'";
  }

  public String doubleQuote(String input) {
    return "\"" + input + "\"";
  }

  public String enclose(String enclosingString, String input) {
    return enclosingString + input + enclosingString;
  }

  public String escapeChars(String input, String charsList) {
    for (int i = 0; i < charsList.length(); i++) {
      char ch = charsList.charAt(i);
      input = input.replace(Character.toString(ch), "\\" + ch);
    }
    return input;
  }
}
