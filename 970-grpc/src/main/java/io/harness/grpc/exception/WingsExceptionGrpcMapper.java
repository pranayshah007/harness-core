/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc.exception;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.ReportTarget.REST_API;

import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;

import com.google.inject.Singleton;
import io.grpc.Status;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class WingsExceptionGrpcMapper implements GrpcExceptionMapper<WingsException> {
  @Override
  public Status toStatus(WingsException throwable) {
    Status status = null;
    if (throwable != null) {
      ExceptionLogger.logProcessedMessages(throwable, MANAGER, log);
      List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(throwable, REST_API);
      if (isNotEmpty(responseMessages)) {
        status = Status.INTERNAL.withDescription(responseMessages.get(0).getMessage()).withCause(throwable);
      } else {
        status = Status.INTERNAL.withDescription(throwable.toString()).withCause(throwable);
      }
    }
    return status;
  }

  @Override
  public Class getClazz() {
    return WingsException.class;
  }
}
