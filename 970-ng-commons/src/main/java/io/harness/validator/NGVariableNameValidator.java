/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.validator;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Set;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
public class NGVariableNameValidator implements ConstraintValidator<NGVariableName, String> {
  public static final String ALLOWED_CHARS_STRING_DEFAULT =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_. ";

  static final Set<String> NOT_ALLOWED_STRING_SET_DEFAULT = Sets.newHashSet("or", "and", "eq", "ne", "lt", "gt", "le",
      "ge", "div", "mod", "not", "null", "true", "false", "new", "var", "return");

  public static final int MAX_ALLOWED_LENGTH = 128;

  @Override
  public void initialize(NGVariableName variableName) {
    // nothing to initialize
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (!StringUtils.isNotBlank(value)) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("cannot be empty.").addConstraintViolation();
      return false;
    }

    if (value.length() > MAX_ALLOWED_LENGTH) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("cannot be more than 128 characters long.").addConstraintViolation();
      return false;
    }

    if (Character.isDigit(value.charAt(0))) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("cannot start variable name with digit.").addConstraintViolation();
      return false;
    }

    boolean containsAllowedChars =
        Sets.newHashSet(Lists.charactersOf(ALLOWED_CHARS_STRING_DEFAULT)).containsAll(Lists.charactersOf(value));
    if (!containsAllowedChars) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("can only contain alphanumeric, and underscore characters.")
          .addConstraintViolation();
      return false;
    }
    boolean containsNotAllowedChars = NOT_ALLOWED_STRING_SET_DEFAULT.contains(value);
    if (containsNotAllowedChars) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate("cannot be equal to reserved words - " + NOT_ALLOWED_STRING_SET_DEFAULT)
          .addConstraintViolation();
      return false;
    }

    return true;
  }
}
