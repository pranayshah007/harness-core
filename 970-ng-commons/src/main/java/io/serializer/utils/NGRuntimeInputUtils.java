/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.serializer.utils;

import io.harness.beans.InputSetValidatorType;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NGRuntimeInputUtils {
  public String extractParameters(String text, String validatorTypeString) {
    String validatorValueString = null;
    // Checking the pattern when field ends with validatorTypeString
    Matcher matcher = Pattern.compile("\\." + validatorTypeString + "\\((.*?)\\)$").matcher(text);
    if (matcher.find()) {
      validatorValueString = matcher.group(1);
      // when any inputSetValidator is present after validatorTypeString construct
      List<Pattern> patterns =
          Arrays.stream(InputSetValidatorType.values())
              .filter(o -> !o.getYamlName().equals(validatorTypeString))
              .map(o -> Pattern.compile("\\." + validatorTypeString + "\\((.*?)\\)." + o.getYamlName() + "\\(.*?\\)$"))
              .collect(Collectors.toList());
      // when .executionInput() is present after validatorTypeString construct.
      patterns.add(Pattern.compile("\\." + validatorTypeString + "\\((.*?)\\).executionInput\\(.*?\\)$"));
      if (!validatorTypeString.equals("default")) {
        patterns.add(Pattern.compile("\\." + validatorTypeString + "\\((.*?)\\).default\\(.*?\\)$"));
      }
      for (Pattern pattern : patterns) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
          String validatorValue = m.group(1);
          // Take the smallest match as default value. <+input>.default("abc").executionInput() can match to "abc" and
          // "abc").executionInput(). We need to take the "abc"
          if (validatorValueString == null || validatorValue.length() < validatorValueString.length()) {
            validatorValueString = validatorValue;
          }
        }
      }
    }
    return validatorValueString;
  }
}
