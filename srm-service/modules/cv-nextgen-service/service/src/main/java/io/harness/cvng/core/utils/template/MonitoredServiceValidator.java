/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.template;

import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Path;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.hibernate.validator.internal.engine.path.PathImpl;

public class MonitoredServiceValidator {
  public static void validateMSDTO(MonitoredServiceDTO monitoredServiceDTO) {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    Set<ConstraintViolation<MonitoredServiceDTO>> violations = validator.validate(monitoredServiceDTO);
    violations.forEach(violation -> {
      throw new RuntimeException(getFieldFromPath(violation.getPropertyPath()) + " " + violation.getMessage());
    });
  }

  private static String getFieldFromPath(Path fieldPath) {
    PathImpl pathImpl = (PathImpl) fieldPath;
    return pathImpl.getLeafNode().getName();
  }
}
