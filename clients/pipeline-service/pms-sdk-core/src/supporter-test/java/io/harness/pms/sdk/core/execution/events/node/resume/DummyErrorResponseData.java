/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.node.resume;

import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.tasks.ErrorResponseData;

import java.util.EnumSet;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DummyErrorResponseData implements ErrorResponseData {
  @Override
  public String getErrorMessage() {
    return "error";
  }

  @Override
  public EnumSet<FailureType> getFailureTypes() {
    return EnumSet.of(FailureType.CONNECTIVITY);
  }

  @Override
  public WingsException getException() {
    return new InvalidRequestException("Dummy Invalid Request Exception");
  }
}
