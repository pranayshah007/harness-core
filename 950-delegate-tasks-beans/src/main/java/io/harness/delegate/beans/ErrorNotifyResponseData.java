/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.FailureType;
import io.harness.exception.WingsException;
import io.harness.tasks.ErrorResponseData;

import java.util.EnumSet;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class ErrorNotifyResponseData implements DelegateTaskNotifyResponseData, ErrorResponseData {
  private EnumSet<FailureType> failureTypes;
  private String errorMessage;
  private WingsException exception;
  private DelegateMetaInfo delegateMetaInfo;
  private boolean expired;
}
