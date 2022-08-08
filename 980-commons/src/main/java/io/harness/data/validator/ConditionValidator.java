/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.data.validator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ConditionValidator implements ConstraintValidator<Condition, Object> {
  private String conditionProperty;
  private String conditionPropertyValue;
  private String[] requiredProperties;
  private String message;

  @Override
  public void initialize(Condition condition) {
    conditionProperty = condition.property();
    conditionPropertyValue = condition.propertyValue();
    requiredProperties = condition.requiredProperties();
    message = condition.message();
  }

  @Override
  public boolean isValid(Object obj, ConstraintValidatorContext constraintValidatorContext) {
    try {
      String objPropertyStrValue = BeanUtils.getProperty(obj, conditionProperty);

      if (conditionPropertyValue != null && conditionPropertyValue.equals(objPropertyStrValue)) {
        return validateRequiredProperties(obj, constraintValidatorContext);
      }
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
      log.error("Unable to do conditional validation for property {}", conditionProperty, ex);
      return false;
    }
    return true;
  }

  private boolean validateRequiredProperties(Object obj, ConstraintValidatorContext context)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    boolean isValid = true;
    for (String requireProperty : requiredProperties) {
      Object requiredValue = BeanUtils.getProperty(obj, requireProperty);
      boolean isNotPresent = Objects.isNull(requiredValue);
      if (isNotPresent) {
        isValid = false;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(requireProperty).addConstraintViolation();
      }
    }

    return isValid;
  }
}
