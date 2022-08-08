/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.ExpressionFunctor;
import io.harness.expression.LateBindingValue;
import io.harness.expression.SecretString;
import io.harness.shell.ScriptType;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Builder
@Slf4j
public class ShellScriptFunctor implements ExpressionFunctor {
  private ScriptType scriptType;

  public String escape(SecretString input) {
    return escape(input.toString());
  }

  public String escape(LateBindingValue input) {
    return escape(input.bind().toString());
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

  public String quote(SecretString input) {
    return quote(input.toString());
  }

  public String quote(LateBindingValue input) {
    return quote(input.bind().toString());
  }

  public String quote(String input) {
    return "\'" + input + "\'";
  }

  public String doubleQuote(SecretString input) {
    return doubleQuote(input.toString());
  }

  public String doubleQuote(LateBindingValue input) {
    return doubleQuote(input.bind().toString());
  }

  public String doubleQuote(String input) {
    return "\"" + input + "\"";
  }

  public String enclose(String enclosingString, SecretString input) {
    return enclose(enclosingString, input.toString());
  }

  public String enclose(String enclosingString, LateBindingValue input) {
    return enclose(enclosingString, input.bind().toString());
  }

  public String enclose(String enclosingString, String input) {
    return enclosingString + input + enclosingString;
  }
}
