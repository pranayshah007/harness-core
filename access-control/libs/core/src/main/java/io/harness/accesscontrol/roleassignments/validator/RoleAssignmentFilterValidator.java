/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.validator;

import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@OwnedBy(HarnessTeam.PL)
public class RoleAssignmentFilterValidator
    implements ConstraintValidator<ValidRoleAssignmentFilter, RoleAssignmentFilter> {
  @Override
  public void initialize(ValidRoleAssignmentFilter constraintAnnotation) {
    // nothing to initialize
  }

  @Override
  public boolean isValid(RoleAssignmentFilter value, ConstraintValidatorContext context) {
    if (!value.getPrincipalFilter().isEmpty()
        && (!value.getPrincipalTypeFilter().isEmpty() || !value.getPrincipalScopeLevelFilter().isEmpty())) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(
              "RoleAssignment Filter can have either a principal filter or a principal type and principal scope level filter.")
          .addConstraintViolation();
      return false;
    } else if (value.getPrincipalTypeFilter().isEmpty() && !value.getPrincipalScopeLevelFilter().isEmpty()) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(
              "RoleAssignment Filter needs a principal type filter for principal scope level filter.")
          .addConstraintViolation();
      return false;
    }
    return true;
  }
}
