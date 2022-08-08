/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.command.ExecutionLogCallback;

import com.microsoft.azure.CloudException;
import com.microsoft.rest.ServiceCallback;
import java.util.concurrent.atomic.AtomicBoolean;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureRestCallBack<T> implements ServiceCallback<T> {
  private Throwable throwable;
  private final AtomicBoolean updateFailed = new AtomicBoolean();
  private final ExecutionLogCallback logCallBack;
  private final String resourceName;

  public AzureRestCallBack(ExecutionLogCallback logCallBack, String resourceName) {
    this.logCallBack = logCallBack;
    this.resourceName = resourceName;
  }

  @Override
  public void failure(Throwable t) {
    throwable = t;
    updateFailed.set(true);
  }

  @Override
  public void success(T result) {
    logCallBack.saveExecutionLog(
        format("Received success response from Azure for VMSS: [%s] update capacity", resourceName), INFO, SUCCESS);
  }

  public boolean updateFailed() {
    return updateFailed.get();
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
