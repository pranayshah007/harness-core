/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.tasks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.FailureType;
import io.harness.exception.WingsException;

import java.util.EnumSet;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class FailureResponseData implements ErrorResponseData {
  String errorMessage;
  EnumSet<FailureType> failureTypes;
  WingsException exception;
  // TODO : Add more fields here for better handling
}
