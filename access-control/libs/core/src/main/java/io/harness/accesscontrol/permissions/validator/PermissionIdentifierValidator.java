/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions.validator;

import static io.harness.accesscontrol.permissions.Permission.PERMISSION_DELIMITER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
public class PermissionIdentifierValidator implements ConstraintValidator<PermissionIdentifier, String> {
  private static final String ALLOWED_CHARS_STRING = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._";
  private static final int MAX_ALLOWED_LENGTH = 128;

  @Override
  public void initialize(PermissionIdentifier constraintAnnotation) {
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
    if (!Sets.newHashSet(Lists.charactersOf(ALLOWED_CHARS_STRING)).containsAll(Lists.charactersOf(value))) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(
              "can only contain small case alphabets, period and underscore characters.")
          .addConstraintViolation();
      return false;
    }
    if (value.split(PERMISSION_DELIMITER).length != 3) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("permission should be of the format \"module_resourceType_action\"")
          .addConstraintViolation();
      return false;
    }
    return true;
  }
}
