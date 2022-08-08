/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ldap;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;

public interface LdapValidator {
  default void validate(Logger log) {
    Set<ConstraintViolation<Object>> violations =
        Validation.buildDefaultValidatorFactory().getValidator().validate(this);
    if (CollectionUtils.isNotEmpty(violations)) {
      for (ConstraintViolation violation : violations) {
        log.error("{}.{} {}", violation.getRootBeanClass().getSimpleName(), violation.getPropertyPath(),
            violation.getMessage());
      }
      throw new ConstraintViolationException(violations);
    }
  }
}
