/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.remote.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ErrorDTO;

import lombok.Getter;

// Whenever there is an api call via NGRestUtils and there is an error, we will throw this exception.
@OwnedBy(HarnessTeam.PIPELINE)
public class HarnessServiceCallException extends WingsException {
  @Getter ErrorDTO errorDTO;

  public HarnessServiceCallException(String message, Throwable cause, ErrorDTO errorDTO) {
    super(message, cause);
    this.errorDTO = errorDTO;
  }
}
