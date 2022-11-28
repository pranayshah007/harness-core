/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.validators;

import io.harness.beans.WithIdentifier;

import java.util.List;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class UniqueIdentifierValidator
    implements ConstraintValidator<UniqueIdentifierCheck, List<? extends WithIdentifier>> {
  @Override
  public void initialize(UniqueIdentifierCheck listUniqueIdentifier) {}

  @Override
  public boolean isValid(
      List<? extends WithIdentifier> withIdentifiers, ConstraintValidatorContext constraintValidatorContext) {
    if (withIdentifiers == null) {
      return true;
    }
    return withIdentifiers.size()
        == withIdentifiers.stream().map(withIdentifier -> withIdentifier.getIdentifier()).distinct().count();
  }
}
