/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidationError {
  private String fieldId;
  private String error;

  private ValidationError() {}

  public static ValidationError of(String field, String error) {
    ValidationError validationError = new ValidationError();
    validationError.setFieldId(field);
    validationError.setError(error);
    return validationError;
  }
}
