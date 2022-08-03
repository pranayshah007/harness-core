/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.execution.utils.EngineExceptionUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class StrategyHelper {
  @Inject private ExceptionManager exceptionManager;

  public static ThrowingSupplier buildResponseDataSupplier(Map<String, ResponseData> responseDataMap) {
    return () -> {
      if (isEmpty(responseDataMap)) {
        return null;
      }
      ResponseData data = responseDataMap.values().iterator().next();
      if (data instanceof ErrorResponseData) {
        if (((ErrorResponseData) data).getException() == null) {
          throw new GeneralException(((ErrorResponseData) data).getErrorMessage());
        }
        throw((ErrorResponseData) data).getException();
      }
      return data;
    };
  }

  public StepResponse handleException(Exception ex) {
    List<ResponseMessage> responseMessages = exceptionManager.buildResponseFromException(ex);
    StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.FAILED);
    FailureInfo failureInfo = EngineExceptionUtils.transformResponseMessagesToFailureInfo(responseMessages);
    TaskNGDataException taskFailureData = ExceptionUtils.cause(TaskNGDataException.class, ex);
    if (taskFailureData != null && taskFailureData.getCommandUnitsProgress() != null) {
      stepResponseBuilder.unitProgressList(taskFailureData.getCommandUnitsProgress().getUnitProgresses());
    }

    return stepResponseBuilder.failureInfo(failureInfo).build();
  }
}
