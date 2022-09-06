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

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@OwnedBy(HarnessTeam.PL)
public class UrlFieldValidator implements ConstraintValidator<UrlField, String> {
  @Override
  public boolean isValid(String s, ConstraintValidatorContext context) {
    if (isBlank(s)) {
      // If the url string is null / empty - return true.
      return true;
    }
    return Http.validUrl(s);
  }
}
