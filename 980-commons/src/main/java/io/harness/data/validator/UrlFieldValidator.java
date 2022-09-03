/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.data.validator;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.network.Http;

import java.net.MalformedURLException;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@OwnedBy(HarnessTeam.PL)
public class UrlFieldValidator implements ConstraintValidator<UrlField, String> {
  private boolean allowBlank;

  @Override
  public void initialize(UrlField constraintAnnotation) {
    allowBlank = constraintAnnotation.allowBlank();
  }

  @Override
  public boolean isValid(String s, ConstraintValidatorContext context) {
    // check for blank
    if (allowBlank && isBlank(s)) {
      return true;
    }
    if (!allowBlank && isBlank(s)) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("Url cannot be empty").addConstraintViolation();
      return false;
    }
    if (Http.validUrl(s)) {
      return true;
    }
    // As a hack - we are removing the path, so that it doesn't get propagated to the UI
    // TODO: Find the right way to implement it

    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(String.format("Invalid url : %s", s)).addConstraintViolation();
    return false;
  }
}
