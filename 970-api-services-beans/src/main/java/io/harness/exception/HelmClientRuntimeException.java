/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.utils.ExceptionUtils;

import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HelmClientRuntimeException extends RuntimeException {
  @Getter final HelmClientException helmClientException;

  public HelmClientRuntimeException(@NotNull HelmClientException helmClientException) {
    super(ExceptionUtils.getMessage(helmClientException));
    this.helmClientException = helmClientException;
  }
}
