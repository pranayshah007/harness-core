/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.data.validator;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class EntityIdentifierValidator implements ConstraintValidator<EntityIdentifier, String> {
  // Start with: Alphabets, characters or Underscore
  // Chars Allowed : Alphanumeric, Underscore, $
  public static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][0-9a-zA-Z_$]{0,127}$");
  public Pattern identifierPattern;
  public static final Set<String> NOT_ALLOWED_WORDS =
      Stream
          .of("or", "and", "eq", "ne", "lt", "gt", "le", "ge", "div", "mod", "not", "null", "true", "false", "new",
              "var", "return", "step", "parallel", "stepGroup", "org", "account", "class", "shellScriptProvisioner")
          .collect(Collectors.toCollection(HashSet::new));
  private static final String reservedKeyword = "_harness_system";
  private boolean allowBlank;
  private boolean allowScoped;
  private int maxLength;

  @Override
  public void initialize(EntityIdentifier constraintAnnotation) {
    maxLength = constraintAnnotation.maxLength();
    allowBlank = constraintAnnotation.allowBlank();
    allowScoped = constraintAnnotation.allowScoped();
    String patternString = String.format("^[a-zA-Z_][0-9a-zA-Z_$]{0,%s}$", maxLength - 1);
    identifierPattern = Pattern.compile(patternString);
  }

  @Override
  public boolean isValid(String identifier, ConstraintValidatorContext context) {
    if (allowBlank && isBlank(identifier)) {
      return true;
    }
    if (!allowBlank && isBlank(identifier)) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("cannot be empty").addConstraintViolation();
      return false;
    }
    if (allowScoped) {
      if (identifier.startsWith("account.")) {
        identifier = identifier.replaceFirst("account.", "");
      } else if (identifier.startsWith("org.")) {
        identifier = identifier.replaceFirst("org.", "");
      }
    }
    if (!matchesIdentifierPattern(identifier)) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(
              String.format("can be %s characters long and can only contain alphanumeric, underscore and $ characters,"
                      + " and not start with a number or $",
                  maxLength))
          .addConstraintViolation();
      return false;
    }
    if (!hasAllowedWords(identifier)) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate(identifier + " is a keyword, so cannot be used")
          .addConstraintViolation();
      return false;
    }
    if (containsReservedWord(identifier)) {
      log.warn("Identifier {} contains harness reserved keyword", identifier);
    }
    return true;
  }

  private boolean containsReservedWord(String identifier) {
    return identifier.startsWith(reservedKeyword);
  }

  @VisibleForTesting
  boolean matchesIdentifierPattern(String identifier) {
    return identifierPattern.matcher(identifier).matches();
  }

  @VisibleForTesting
  boolean hasAllowedWords(String identifier) {
    return identifier != null && !NOT_ALLOWED_WORDS.contains(identifier);
  }
}
