/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.data.validator;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.harness.annotations.dev.OwnedBy;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;

@OwnedBy(CDP)
@Documented
@Repeatable(Conditions.class)
@Constraint(validatedBy = {ConditionValidator.class})
@Target({TYPE})
@Retention(RUNTIME)
@ReportAsSingleViolation
public @interface Condition {
  String message() default "Condition not satisfied";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};

  String property();
  String propertyValue();

  String[] requiredProperties();
}
