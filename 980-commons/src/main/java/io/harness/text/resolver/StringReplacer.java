/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.text.resolver;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_EXPRESSION_ENGINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class StringReplacer {
  private static final char ESCAPE_CHAR = '\\';

  private final ExpressionResolver expressionResolver;
  private final char[] expressionPrefix;
  private final char[] expressionSuffix;

  public StringReplacer(ExpressionResolver expressionResolver, String expressionPrefix, String expressionSuffix) {
    this.expressionResolver = expressionResolver;
    this.expressionPrefix = expressionPrefix.toCharArray();
    this.expressionSuffix = expressionSuffix.toCharArray();
  }

  public String replace(String source) {
    if (source == null) {
      return null;
    }

    StringBuffer buf = new StringBuffer(source);
    return substitute(buf, source, false).getFinalExpressionValue();
  }

  public StringReplacerResponse replaceWithRenderCheck(String source) {
    if (source == null) {
      return null;
    }

    StringBuffer buf = new StringBuffer(source);
    return substitute(buf, source, true);
  }

  private StringReplacerResponse substitute(StringBuffer buf, String source, boolean checkRenderExpression) {
    boolean altered = false;
    boolean onlyRenderedExpressions = true;
    int bufEnd = buf.length();
    int pos = 0;
    while (pos < bufEnd) {
      boolean hasPrefix = isMatch(expressionPrefix, buf, pos, bufEnd);
      if (!hasPrefix) {
        pos++;
        continue;
      }

      // Found expression prefix
      int expressionStartPos = pos;
      pos += expressionPrefix.length;
      boolean foundSuffix;
      int nestedExpressionCount = 0;
      while (pos < bufEnd) {
        if (isMatch(expressionPrefix, buf, pos, bufEnd)) {
          // Found a nested expression prefix
          nestedExpressionCount++;
          pos += expressionPrefix.length;
          continue;
        }

        foundSuffix = isMatch(expressionSuffix, buf, pos, bufEnd);
        if (!foundSuffix) {
          if (isMatch(ESCAPE_CHAR, buf, pos, bufEnd)) {
            // Possible case: <+ abc \> def >
            if (isMatch(expressionSuffix, buf, pos + 1, bufEnd)) {
              // If we find an escaped suffix, we delete the escape char and skip over the suffix
              buf.deleteCharAt(pos);
              bufEnd = buf.length();
              pos += expressionSuffix.length;
            } else {
              // If the escape char doesn't escape the suffix, treat it as a normal character
              pos++;
            }
          } else {
            pos++;
          }
          continue;
        }

        // Found expression suffix
        pos += expressionSuffix.length;
        if (nestedExpressionCount > 0) {
          // Found a nested expression suffix
          nestedExpressionCount--;
          continue;
        }

        // Get whole expression
        int expressionEndPos = pos;
        String expressionWithDelimiters = getModifiedExpression(expressionStartPos, expressionEndPos, buf);
        String expression = expressionWithDelimiters.substring(
            expressionPrefix.length, expressionWithDelimiters.length() - expressionSuffix.length);

        // Resolve the expression
        String expressionValue = expressionResolver.resolve(expression);
        if (checkRenderExpression
            && checkIfExpressionValueCanBeConcatenated(expressionValue, expressionStartPos, expressionEndPos, buf)) {
          expressionValue = (String) expressionResolver.getContextValue(expressionValue);
        } else {
          onlyRenderedExpressions = false;
        }
        buf.replace(expressionStartPos, expressionEndPos, expressionValue);
        pos += expressionValue.length() - expressionWithDelimiters.length();
        bufEnd = buf.length();
        altered = altered || !expressionWithDelimiters.equals(expressionValue);
        break;
      }
    }
    return StringReplacerResponse.builder()
        .finalExpressionValue(altered ? buf.toString() : source)
        .originalExpressionAltered(altered)
        // this is added for inputs where there are no expressions, example (true != false)
        .onlyRenderedExpressions(altered && onlyRenderedExpressions)
        .build();
  }

  /**
   * This method checks if string expression can be rendered (to concatenate or not)
   * Based on if left substring of expression or right substring of expression has first non-space char other than +
   * operator.
   * Note: Both default concatenate and + operator at same level will not work, use either of them
   * Example: <+f><+g>harness or 'harness' + <+f><+g>
   * @param expressionValue
   * @param expressionStartPos
   * @param expressionEndPos This pointer is one index ahead of actual index of '>'
   * @param buf
   * @return
   */
  private boolean checkIfExpressionValueCanBeConcatenated(
      String expressionValue, int expressionStartPos, int expressionEndPos, StringBuffer buf) {
    Object contextValue = expressionResolver.getContextValue(expressionValue);
    if (expressionValue == null || contextValue == null) {
      return false;
    }

    if (!(contextValue instanceof String)) {
      return false;
    }
    // Complete buf is expression
    if (expressionStartPos == 0 && expressionEndPos == buf.length()) {
      return false;
    }

    // Check if right substring has method invocation, then return false
    if (checkIfValueHasMethodInvocation(buf, expressionEndPos)) {
      return false;
    }

    // Check on left if any first string mathematical operator found or not
    expressionStartPos--;
    while (expressionStartPos >= 0) {
      char c = buf.charAt(expressionStartPos);
      if (c == '(' || c == '[' || c == ',') {
        // expression is inside a method invocation, thus don't take decision of concatenate from left substring
        // , denotes it could be part of parameter in method, example <+json.list("$", <+var1>)>, then var1 shouldn't be
        // concatenated.
        // Expression inside [], square brackets denote get method, thus should be also be considered.
        break;
      } else if (c == ':') {
        // Checking : belongs to ternary operator or not, if not concatenate it
        if (!buf.toString().contains("?")) {
          return true;
        } else {
          return false;
        }
      } else if (checkIfStringMathematicalOperator(c) || checkBooleanOperators(buf, expressionStartPos, true)) {
        return false;
      } else if (!skipNonCriticalCharacters(c)) {
        return true;
      }
      expressionStartPos--;
    }

    // Check on right if any first string mathematical operator found or not
    while (expressionEndPos <= buf.length() - 1) {
      char c = buf.charAt(expressionEndPos);
      if (c == ')' || c == ']' || c == ',') {
        // expression is inside a method invocation, thus don't take decision of concatenate from right substring
        // , denotes it could be part of parameter in method, example <+json.list("$", <+var1>)>, then var1 shouldn't be
        // concatenated.
        // Expression inside [], square brackets denote get method, thus should be also be considered.
        break;
      } else if (c == ':') {
        // Checking : belongs to ternary operator or not, if not concatenate it
        if (!buf.toString().contains("?")) {
          return true;
        } else {
          return false;
        }
      } else if (checkIfStringMathematicalOperator(c) || checkBooleanOperators(buf, expressionEndPos, false)) {
        return false;
      } else if (!skipNonCriticalCharacters(c)) {
        return true;
      }
      expressionEndPos++;
    }

    return false;
  }

  private boolean checkIfValueHasMethodInvocation(StringBuffer buf, int expressionEndPos) {
    // Right substring
    CharSequence charSequence = buf.subSequence(expressionEndPos, buf.length());
    Pattern pattern = Pattern.compile("\\.\\w+\\(");
    Matcher matcher = pattern.matcher(charSequence);
    boolean isMatch = matcher.find();
    Pattern pattern2 = Pattern.compile("^\\.\\w+\\(");
    Matcher matcher2 = pattern2.matcher(charSequence);
    boolean isMatch2 = matcher2.find();
    Pattern pattern3 = Pattern.compile("^\\)\\.\\w+\\(");
    Matcher matcher3 = pattern3.matcher(charSequence);
    boolean isMatch3 = matcher3.find();
    if (isMatch != (isMatch2 || isMatch3)) {
      log.info("[Expression Method Invocation]: charSequence: {}, buf: {}", charSequence, buf);
    }
    return isMatch;
  }

  private boolean checkBooleanOperators(StringBuffer s, int currentPos, boolean leftSubString) {
    if (!Character.isLowerCase(s.charAt(currentPos))) {
      return false;
    }

    // https://commons.apache.org/proper/commons-jexl/reference/syntax.html
    Set<String> jexlKeywordOperators = Set.of("or", "eq", "ne", "and", "not", "size", "empty");

    int minLength = 2;
    int maxLength = 5;

    // checking if any of above keywords separated by space or '\n' is present in the expression
    if (leftSubString) {
      for (int i = minLength; i <= maxLength; i++) {
        if (currentPos - i + 1 >= 0 && currentPos + 1 < s.length()) {
          String substring = s.substring(currentPos - i + 1, currentPos + 1).trim();
          if (jexlKeywordOperators.contains(substring) && currentPos - i >= 0
              && (s.charAt(currentPos + 1) == ' ' || s.charAt(currentPos + 1) == '\n')
              && (s.charAt(currentPos - i) == ' ' || s.charAt(currentPos - i) == '\n')) {
            return true;
          }
        }
      }
    }

    for (int i = minLength; i <= maxLength; i++) {
      if (currentPos >= 0 && currentPos + i < s.length()) {
        String substring = s.substring(currentPos, currentPos + i).trim();
        if (jexlKeywordOperators.contains(substring) && currentPos - 1 >= 0
            && (s.charAt(currentPos - 1) == ' ' || s.charAt(currentPos - 1) == '\n')
            && (s.charAt(currentPos + i) == ' ' || s.charAt(currentPos + i) == '\n')) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean checkIfStringMathematicalOperator(char c) {
    // + operator for string addition
    // = -> for == comparison operation
    // ? -> for ternary operator
    // & -> && AND operation
    // | -> || OR operator
    // ! -> != operator
    // =~ and !~ regex match and its negate jexl operators
    // =^ and !^ startsWith and its negate operator
    // =$ and !$ endsWith and its negate operator
    return c == '+' || c == '=' || c == '?' || c == '&' || c == '|' || c == '!' || c == '~' || c == '^' || c == '$';
  }

  private boolean skipNonCriticalCharacters(char c) {
    return c == ' ' || c == '(' || c == ')' || c == ';' || c == '\n';
  }

  private static boolean isMatch(char ch, StringBuffer buf, int bufStart, int bufEnd) {
    return bufStart < bufEnd && buf.charAt(bufStart) == ch;
  }

  private static boolean isMatch(char[] str, StringBuffer buf, int bufStart, int bufEnd) {
    if (bufStart + str.length > bufEnd) {
      return false;
    }
    for (int i = 0, j = bufStart; i < str.length; i++, j++) {
      if (str[i] != buf.charAt(j)) {
        return false;
      }
    }
    return true;
  }

  // When expression is wrapped around quotes, the characters like " and \ will be escaped in it. So, when we are
  // extracting the expression from buf we need to unescape them.
  private String getModifiedExpression(int expressionStartPos, int expressionEndPos, StringBuffer buf) {
    String expression = buf.substring(expressionStartPos, expressionEndPos);
    if (expressionStartPos > 0 && buf.charAt(expressionStartPos - 1) == '\"' && expressionEndPos < buf.length()
        && buf.charAt(expressionEndPos) == '\"') {
      log.info("[String Replacer] expression: {}, unescaped expression: {}", expression,
          StringEscapeUtils.unescapeJson(expression));
    }
    return expression;
  }
}
