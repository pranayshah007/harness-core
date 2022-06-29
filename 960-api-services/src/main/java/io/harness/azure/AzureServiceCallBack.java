/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;

import com.microsoft.azure.CloudException;
import com.microsoft.rest.ServiceCallback;
import java.util.concurrent.atomic.AtomicBoolean;

@OwnedBy(HarnessTeam.CDP)
public class AzureServiceCallBack implements ServiceCallback<Void> {
  private Throwable throwable;
  private final LogCallback logCallback;
  private final String operationName;
  private final AtomicBoolean callFailed = new AtomicBoolean();
  private final AtomicBoolean callCompleted = new AtomicBoolean();

  public AzureServiceCallBack(LogCallback logCallback, String operationName) {
    this.logCallback = logCallback;
    this.operationName = operationName;
  }

  @Override
  public void failure(Throwable throwable) {
    this.throwable = throwable;
    callFailed.set(true);
    callCompleted.set(true);
    logCallback.saveExecutionLog(
        String.format("Operation - [%s] has failed. Error - [%s]", operationName, throwable.getMessage()));
  }

  @Override
  public void success(Void t) {
    callCompleted.set(true);
    logCallback.saveExecutionLog(String.format("Operation - [%s] was success", operationName));
  }

  public boolean callFailed() {
    return callFailed.get();
  }

  public String getErrorMessage() {
    String failureMessage = failureMessage();
    String bodyMessage = getBodyMessage();
    if (bodyMessage == null) {
      return failureMessage;
    } else {
      return format("%s: %s", failureMessage, bodyMessage);
    }
  }

  private String failureMessage() {
    return throwable.getMessage();
  }

  private String getBodyMessage() {
    if (throwable instanceof CloudException) {
      CloudException cloudException = (CloudException) throwable;
      if (cloudException.body() != null) {
        return cloudException.body().message();
      }
    }
    return null;
  }
}
