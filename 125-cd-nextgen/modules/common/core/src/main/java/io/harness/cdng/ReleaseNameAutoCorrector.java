/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class ReleaseNameAutoCorrector {
  private static final String DNS_COMPLIANCE_REGEX = "([a-z0-9][-a-z0-9_.]*)?[a-z0-9]";
  private static final String FIRST_CHAR_ALPHABETIC_REGEX = "^[a-z].*$";
  private static final String SECOND_CHAR_ALPHABETIC_REGEX = "^.[a-z].*$";
  private static final String NON_ALPHA_NUMERIC_CHARS_REGEX = "[^a-z1-9]";
  private static final String AT_LEAST_ONE_HYPHEN_REGEX = "-+";
  private static final int CHARS_VALIDATION_MAX_LENGTH = 63;

  public static boolean isDnsCompliant(String input) {
    return isNotEmpty(input) && CHARS_VALIDATION_MAX_LENGTH >= input.length() && input.matches(DNS_COMPLIANCE_REGEX);
  }

  public static String makeDnsCompliant(String input) {
    String result = input.toLowerCase();

    if (countLowerCaseAlphaNumericChars(result) == 0) {
      return EMPTY;
    }
    result = startWithAlphabeticChar(result);
    result = replaceNonAlphaNumericChars(result, "-");

    if (result.length() > 63) {
      result = result.substring(0, 63);
    }
    result = removeTrailingNonAlphaNumericChars(result);

    return result;
  }

  public static String removeTrailingNonAlphaNumericChars(String input) {
    StringBuilder sb = new StringBuilder(input);

    while (sb.length() > 0 && !isLowercaseAlphaNumeric(sb.charAt(sb.length() - 1))) {
      sb.deleteCharAt(sb.length() - 1);
    }

    return sb.toString();
  }

  public static String replaceNonAlphaNumericChars(String input, String replacement) {
    String result = input;

    // replace all non-alphanumeric chars with '-'
    result = result.replaceAll(NON_ALPHA_NUMERIC_CHARS_REGEX, replacement);

    // remove consecutive '-' chars with single '-'
    result = result.replaceAll(AT_LEAST_ONE_HYPHEN_REGEX, replacement);

    return result;
  }

  public static String startWithAlphabeticChar(String input) {
    if (isEmpty(input)) {
      return EMPTY;
    }

    if (input.length() == 1 && !isLowercaseAlphabetic(input.charAt(0))) {
      // Don't allow release name with single non-alphabetic char like '7'
      return EMPTY;
    }

    String result = input;
    if (!result.matches(FIRST_CHAR_ALPHABETIC_REGEX)) {
      // first char is not alphabetic

      if (result.matches(SECOND_CHAR_ALPHABETIC_REGEX)) {
        // second char is alphabetic, just remove the first char from the name
        result = result.substring(1);
      } else {
        // first 2 chars(at least) are not alphabetic OR string has 1 char ONLY. Change first char to 'r'
        StringBuilder sb = new StringBuilder(result);
        sb.setCharAt(0, 'r');
        result = sb.toString();
      }
    }
    return result;
  }

  public static long countLowerCaseAlphaNumericChars(String input) {
    return input.chars().mapToObj(i -> (char) i).filter(ReleaseNameAutoCorrector::isLowercaseAlphaNumeric).count();
  }
  private static boolean isLowercaseAlphaNumeric(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
  }

  private static boolean isLowercaseAlphabetic(char c) {
    return (c >= 'a' && c <= 'z');
  }
}
