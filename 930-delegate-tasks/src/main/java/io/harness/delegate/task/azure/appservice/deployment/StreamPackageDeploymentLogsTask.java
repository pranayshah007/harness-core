/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.FAIL_DEPLOYMENT_ERROR_MSG;
import static io.harness.azure.model.AzureConstants.FAIL_LOG_STREAMING;
import static io.harness.azure.model.AzureConstants.LOG_STREAM_SUCCESS_MSG;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.utility.AzureLogParser;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;

import com.microsoft.azure.CloudException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joda.time.DateTime;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.schedulers.Schedulers;

@OwnedBy(CDP)
public class StreamPackageDeploymentLogsTask implements Runnable {
  AzureWebClientContext azureWebClientContext;
  AzureWebClient azureWebClient;
  String slotName;
  LogCallback logCallback;
  AzureLogParser logParser;
  private Subscription subscription;
  private final DateTime startTime;
  private final AtomicBoolean operationCompleted = new AtomicBoolean();
  private final AtomicBoolean operationFailed = new AtomicBoolean();
  private String errorLog;

  public StreamPackageDeploymentLogsTask(AzureWebClientContext azureWebClientContext, AzureWebClient azureWebClient,
      String slotName, LogCallback logCallback, DateTime startTime) {
    this.azureWebClientContext = azureWebClientContext;
    this.azureWebClient = azureWebClient;
    this.slotName = slotName;
    this.logCallback = logCallback;
    this.logParser = new AzureLogParser();
    this.startTime = startTime;
  }

  private void validateAndLog(String log) {
    if (!logParser.shouldLog(log)) {
      return;
    }
    logCallback.saveExecutionLog(log, INFO);
  }

  protected void streamLogs(String s) {
    if (!isNewLog(s)) {
      return;
    }
    String log = logParser.removeTimestamp(s);
    validateAndLog(log);
    if (logParser.checkIsSuccessDeployment(log)) {
      operationCompleted.set(true);
      logCallback.saveExecutionLog(String.format(LOG_STREAM_SUCCESS_MSG, slotName), INFO, SUCCESS);
      subscription.unsubscribe();
    }

    if (logParser.checkIfFailed(log)) {
      operationCompleted.set(true);
      operationFailed.set(true);
      errorLog = log;
      logCallback.saveExecutionLog(String.format(FAIL_DEPLOYMENT_ERROR_MSG, slotName, log), ERROR, FAILURE);
      subscription.unsubscribe();
      throw new InvalidRequestException(log);
    }
  }

  public boolean isNewLog(String log) {
    Optional<DateTime> logTime = logParser.parseTime(log);
    return logTime.map(startTime::isBefore).orElse(false);
  }

  @Override
  public void run() {
    Observable<String> logStreamObservable = azureWebClient.streamDeploymentLogsAsync(azureWebClientContext, slotName);
    Subscriber<String> streamSubscriber = new Subscriber<String>() {
      @Override
      public void onCompleted() {
        operationCompleted.set(true);
        unsubscribe();
      }

      @Override
      public void onError(Throwable e) {
        if (!operationCompleted.get()) {
          String errorMessage = getErrorMessage(e);
          errorLog = errorMessage;
          logCallback.saveExecutionLog(
              color(
                  String.format(FAIL_LOG_STREAMING, slotName, isEmpty(errorMessage) ? "" : errorMessage), White, Bold),
              INFO, SUCCESS);
        }
        operationCompleted.set(true);
        unsubscribe();
      }

      @Override
      public void onNext(String s) {
        if (!operationCompleted.get()) {
          streamLogs(s);
        } else {
          unsubscribe();
        }
      }
    };
    subscription = streamSubscriber;
    logStreamObservable.subscribeOn(Schedulers.newThread()).subscribe(streamSubscriber);
  }

  public boolean operationFailed() {
    return operationFailed.get();
  }

  public boolean operationCompleted() {
    return operationCompleted.get();
  }

  public String getErrorLog() {
    return errorLog;
  }

  public void unsubscribe() {
    subscription.unsubscribe();
  }

  private String getErrorMessage(Throwable e) {
    String failureMessage = failureMessage(e);
    String bodyMessage = getBodyMessage(e);
    if (bodyMessage == null) {
      return failureMessage;
    } else {
      return format("%s: %s", failureMessage, bodyMessage);
    }
  }

  private String failureMessage(Throwable throwable) {
    Throwable cause = throwable.getCause();
    if (cause != null) {
      return cause.getMessage();
    }
    return throwable.toString();
  }

  private String getBodyMessage(Throwable throwable) {
    if (throwable instanceof CloudException) {
      CloudException cloudException = (CloudException) throwable;
      if (cloudException.body() != null) {
        return cloudException.body().message();
      }
    }
    return null;
  }
}
