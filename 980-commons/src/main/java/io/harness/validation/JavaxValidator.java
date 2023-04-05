/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.validation;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

@Singleton
public class JavaxValidator {
  public static void validateOrThrow(Object obj) {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    Set<ConstraintViolation<Object>> violations = validator.validate(obj);
    if (isNotEmpty(violations)) {
      StringBuilder sb = new StringBuilder();
      sb.append("Validation failed with errors: ");
      for (ConstraintViolation<Object> violation : violations) {
        sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append(", ");
      }
      sb.delete(sb.length() - 2, sb.length()); // remove the last comma and space
      throw new InvalidRequestException(sb.toString());
    }
  }
}
