/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@OwnedBy(CE)
@EqualsAndHashCode(callSuper = true)
public class CCMConnectorRuntimeException extends RuntimeException {
  private String message;
  private String hint;
  private String explanation;
  private int code;

  public CCMConnectorRuntimeException(String message, String hint, String explanation, int code) {
    this.message = message;
    this.hint = hint;
    this.explanation = explanation;
    this.code = code;
  }
}
