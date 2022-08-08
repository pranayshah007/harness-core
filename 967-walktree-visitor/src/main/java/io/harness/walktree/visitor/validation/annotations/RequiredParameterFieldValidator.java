/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.validation.annotations;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class RequiredParameterFieldValidator implements ConstraintValidator<Required, Object> {
  @Override
  public void initialize(Required required) {
    // do Nothing
  }

  @Override
  public boolean isValid(Object s, ConstraintValidatorContext constraintValidatorContext) {
    // To be implemented with InputSet
    return s != null;
  }
}
