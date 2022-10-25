/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.validation;

import io.harness.reflection.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorContextImpl;

@Slf4j
public class OneOfFieldValidator implements ConstraintValidator<OneOfField, Object> {
  Set<String> fields = new HashSet<>();
  boolean nullable;
  @Override
  public void initialize(OneOfField oneOfField) {
    Collections.addAll(fields, oneOfField.fields());
    nullable = oneOfField.nullable();
  }

  @Override
  public boolean isValid(Object object, ConstraintValidatorContext context) {
    Set<String> fieldNamesPresent = new HashSet<>();
    final int countOfFieldsOfOneOf = fields.stream()
                                         .map(fieldName -> {
                                           final Field field =
                                               ReflectionUtils.getFieldByName(object.getClass(), fieldName);
                                           if (field == null) {
                                             log.error("Field [{}] not found in object", fieldName);
                                           } else {
                                             field.setAccessible(true);
                                             try {
                                               final Object fieldValue = field.get(object);
                                               if (fieldValue != null) {
                                                 fieldNamesPresent.add(fieldName);
                                                 return 1;
                                               }
                                             } catch (IllegalAccessException e) {
                                               // Should never reach here.
                                               log.error("Field [{}] not found in object", fieldName);
                                             }
                                           }
                                           return 0;
                                         })
                                         .mapToInt(Integer::valueOf)
                                         .sum();

    boolean valid = (nullable && (countOfFieldsOfOneOf == 0 || countOfFieldsOfOneOf == 1))
        || (!nullable && countOfFieldsOfOneOf == 1);
    if (!valid && context != null && context instanceof ConstraintValidatorContextImpl) {
      ((ConstraintValidatorContextImpl) context).addMessageParameter("${fields}", fieldNamesPresent.toString());
    }
    return valid;
  }
}
