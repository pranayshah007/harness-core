/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eraro;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.eraro.Level.ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.FailureType;

import java.util.EnumSet;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

@OwnedBy(PL)
@Value
@Builder
public class ResponseMessage {
  @Default ErrorCode code = DEFAULT_ERROR_CODE;
  @Default Level level = ERROR;

  String message;
  Throwable exception;
  @Default EnumSet<FailureType> failureTypes = EnumSet.noneOf(FailureType.class);
}
