/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.common.validator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.HibernateValidator;

@OwnedBy(CDP)
public class Validators {
  private Validators() {}

  private static final javax.validation.Validator jsr380Validator =
      Validation.byProvider(HibernateValidator.class)
          .configure()
          .addProperty("hibernate.validator.fail_fast", "true")
          .buildValidatorFactory()
          .usingContext()
          .getValidator();

  public static <T> void validate(T t, Validator<T> validator) {
    validator.validate(t);
  }

  public static <X extends Throwable> void validateJsr380FailFast(
      Object obj, Function<String, ? extends X> exceptionFunction) throws X {
    Set<ConstraintViolation<Object>> violations = jsr380Validator.validate(obj);
    if (!violations.isEmpty()) {
      throw exceptionFunction.apply(toString(violations));
    }
  }

  private static String toString(Set<? extends ConstraintViolation<?>> constraintViolations) {
    return constraintViolations.stream()
        .map(cv -> cv == null ? StringUtils.EMPTY : cv.getMessage())
        .collect(Collectors.joining(", "));
  }
}
